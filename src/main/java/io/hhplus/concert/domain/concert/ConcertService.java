package io.hhplus.concert.domain.concert;

import io.hhplus.concert.common.enums.ErrorCode;
import io.hhplus.concert.common.enums.ReservationStatusType;
import io.hhplus.concert.common.exception.CustomException;
import io.hhplus.concert.domain.concert.dto.ConcertOptionInfo;
import io.hhplus.concert.domain.concert.dto.SeatInfo;
import io.hhplus.concert.domain.concert.dto.SeatPriceInfo;
import io.hhplus.concert.domain.concert.model.ConcertOption;
import io.hhplus.concert.domain.concert.model.Reservation;
import io.hhplus.concert.domain.concert.model.Seat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ConcertService {

    private ConcertRepository concertRepository;

    public List<ConcertOptionInfo> findConcerts(LocalDateTime reserveAt) {
        List<ConcertOption> options = concertRepository.findAvailableConcertOptions(reserveAt);

        List<ConcertOptionInfo> concertOptionInfoList = new ArrayList<>();
        for (ConcertOption option : options) {
            concertOptionInfoList.add(
                    new ConcertOptionInfo(option.getId(),
                            option.getConcert().getTitle(),
                            option.getPrice(),
                            option.getSeatQuantity(),
                            option.getPurchaseLimit(),
                            option.getReserveFrom(),
                            option.getReserveUntil(),
                            option.getStartAt(),
                            option.getEndAt()
                    ));
        }

        return concertOptionInfoList;
    }

    public List<SeatInfo> findSeats(Long concertOptionId) {
        List<Seat> seats = concertRepository.findSeatByConcertOptionId(concertOptionId);
        List<SeatInfo> seatInfoList = new ArrayList<>();
        for (Seat seat : seats) {
            seatInfoList.add(new SeatInfo(seat.getId(), seat.getNumber(), seat.getStatus()));
        }

        return seatInfoList;
    }

    /**
     * 좌석 예약 메서드
     *
     * @param seatIdList 좌석테이블 ID 리스트
     * @return List<Reservation> 성공한 좌석에 대한 예약 리스트
     */
    public List<Reservation> reserveSeats(List<Long> seatIdList, Long userId) {
        List<Seat> seats = concertRepository.findSeatByIdIn(seatIdList);

        seats = seats.stream()
                .filter(seat -> seat.getStatus().equals(ReservationStatusType.AVAILABLE))
                .toList();

        List<Reservation> reservationList = new ArrayList<>();
        LocalDateTime reservedAt = LocalDateTime.now();

        for (Seat seat : seats) {
            if (!seat.getStatus().equals(ReservationStatusType.AVAILABLE)) throw new CustomException(ErrorCode.RESERVATION_CONFLICT);

            seat.setStatus(ReservationStatusType.TEMPORARY);

            Reservation reservation = new Reservation(seat.getId(), userId, reservedAt);
            reservation = concertRepository.saveReservation(reservation);
            reservationList.add(reservation);
        }

        return reservationList;
    }

    public List<SeatPriceInfo> findReservedPrice(Long userId) {
        List<Reservation> reservations = concertRepository.findReservationByReservedBy(userId);

        if (reservations.isEmpty()) return List.of();

        List<SeatPriceInfo> seatPriceInfoList = new ArrayList<>();
        for (Reservation reservation : reservations) {
            ConcertOption concertOption = concertRepository.findConcertOptionBySeatId(reservation.getSeatId());
            seatPriceInfoList.add(new SeatPriceInfo(reservation.getSeatId(), concertOption.getPrice()));
        }

        return seatPriceInfoList;
    }

    public void resetReservation() {
        List<Seat> temporarilyReservedSeats = concertRepository.findSeatByStatus(ReservationStatusType.TEMPORARY);

        if (temporarilyReservedSeats.isEmpty()) return;

        LocalDateTime reservationLifetime = LocalDateTime.now().minusMinutes(Reservation.LIFETIME);

        for (Seat seat : temporarilyReservedSeats) {
            List<Reservation> reservations = concertRepository.findReservationBySeatId(seat.getId());

            if (reservations.isEmpty()) continue;

            for (Reservation reservation : reservations) {
                if (reservation.getReservedAt().isBefore(reservationLifetime)) {
                    seat.setStatus(ReservationStatusType.AVAILABLE);
                    concertRepository.deleteReservationById(reservation.getId());
                }
            }
        }
    }
}
