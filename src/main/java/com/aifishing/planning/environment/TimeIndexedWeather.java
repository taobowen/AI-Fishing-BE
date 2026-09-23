package com.aifishing.planning.environment;

import com.aifishing.strategy.weather.WeatherAvailability;
import com.aifishing.strategy.weather.WeatherContext;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Interpolates a persisted Open-Meteo snapshot at an Instant. Does not fetch weather.
 */
public final class TimeIndexedWeather {

    private final WeatherContext snapshot;
    private final ZoneId zone;
    private final List<TimedHour> hours;

    private TimeIndexedWeather(WeatherContext snapshot, ZoneId zone, List<TimedHour> hours) {
        this.snapshot = snapshot;
        this.zone = zone;
        this.hours = hours;
    }

    public static TimeIndexedWeather from(WeatherContext snapshot, ZoneId zone) {
        ZoneId resolved = zone == null ? ZoneId.of("UTC") : zone;
        List<TimedHour> timed = new ArrayList<>();
        if (snapshot != null && snapshot.hours() != null) {
            LocalDate rollingDate = snapshot.forecastDate();
            LocalTime previousTime = null;
            for (WeatherContext.HourlyWeather hour : snapshot.hours()) {
                if (hour == null || hour.time() == null) {
                    continue;
                }
                LocalDate date = hour.date();
                if (date == null) {
                    if (rollingDate == null) {
                        continue;
                    }
                    if (previousTime != null && hour.time().isBefore(previousTime)) {
                        rollingDate = rollingDate.plusDays(1);
                    }
                    date = rollingDate;
                } else {
                    rollingDate = date;
                }
                previousTime = hour.time();
                Instant at = ZonedDateTime.of(date, hour.time(), resolved).toInstant();
                timed.add(new TimedHour(at, hour));
            }
            timed.sort(Comparator.comparing(TimedHour::at));
        }
        return new TimeIndexedWeather(snapshot, resolved, List.copyOf(timed));
    }

    public WeatherContext snapshot() {
        return snapshot;
    }

    public boolean forecastAvailable() {
        return snapshot != null && snapshot.availability() == WeatherAvailability.FORECAST_AVAILABLE;
    }

    public WeatherSample at(Instant instant) {
        if (instant == null) {
            return fromAggregates(null);
        }
        if (hours.isEmpty()) {
            return fromAggregates(instant);
        }
        if (instant.isBefore(hours.get(0).at()) || hours.size() == 1) {
            return fromHour(instant, hours.get(0).hour(), 0);
        }
        TimedHour last = hours.get(hours.size() - 1);
        if (!instant.isBefore(last.at())) {
            return fromHour(instant, last.hour(), 0);
        }
        for (int i = 0; i < hours.size() - 1; i++) {
            TimedHour left = hours.get(i);
            TimedHour right = hours.get(i + 1);
            if (!instant.isAfter(right.at())) {
                long span = right.at().toEpochMilli() - left.at().toEpochMilli();
                double t = span <= 0 ? 0 : (instant.toEpochMilli() - left.at().toEpochMilli()) / (double) span;
                return lerp(instant, left.hour(), right.hour(), t);
            }
        }
        return fromHour(instant, last.hour(), 0);
    }

    /**
     * Conservative interval wind: maximum of departure, midpoint, and arrival samples.
     */
    public WeatherSample intervalMaximumWind(Instant from, Instant to) {
        Instant start = from == null ? to : from;
        Instant end = to == null ? from : to;
        if (start == null) {
            return fromAggregates(null);
        }
        if (end == null || !end.isAfter(start)) {
            return at(start);
        }
        Instant mid = Instant.ofEpochMilli((start.toEpochMilli() + end.toEpochMilli()) / 2);
        WeatherSample a = at(start);
        WeatherSample b = at(mid);
        WeatherSample c = at(end);
        WeatherSample max = a;
        max = higherWind(max, b);
        max = higherWind(max, c);
        return max;
    }

    private static WeatherSample higherWind(WeatherSample current, WeatherSample candidate) {
        double cw = current.windSpeedKmh() == null ? Double.NEGATIVE_INFINITY : current.windSpeedKmh();
        double nw = candidate.windSpeedKmh() == null ? Double.NEGATIVE_INFINITY : candidate.windSpeedKmh();
        return nw > cw ? candidate : current;
    }

    private WeatherSample fromAggregates(Instant instant) {
        if (snapshot == null) {
            return new WeatherSample(instant, null, null, null, null, null, null, null, null, null);
        }
        Double from = snapshot.windDirectionDeg();
        Double flow = from == null ? null : AzimuthConvention.flowFromMeteorological(from);
        return new WeatherSample(
                instant,
                snapshot.windSpeedKmh(),
                from == null ? null : AzimuthConvention.normalize(from),
                flow,
                snapshot.cloudCoverPercent(),
                snapshot.airTemperatureC(),
                snapshot.precipitationMm(),
                snapshot.pressureHpa(),
                null,
                null
        );
    }

    private static WeatherSample fromHour(Instant instant, WeatherContext.HourlyWeather hour, double ignored) {
        Double from = hour.windDirectionDeg();
        Double flow = from == null ? null : AzimuthConvention.flowFromMeteorological(from);
        return new WeatherSample(
                instant,
                hour.windSpeedKmh(),
                from == null ? null : AzimuthConvention.normalize(from),
                flow,
                hour.cloudCoverPercent(),
                hour.airTemperatureC(),
                hour.precipitationMm(),
                hour.pressureHpa(),
                hour.shortwaveRadiation(),
                hour.directRadiation()
        );
    }

    private static WeatherSample lerp(
            Instant instant,
            WeatherContext.HourlyWeather left,
            WeatherContext.HourlyWeather right,
            double t
    ) {
        Double from = lerpAngle(left.windDirectionDeg(), right.windDirectionDeg(), t);
        Double flow = from == null ? null : AzimuthConvention.flowFromMeteorological(from);
        return new WeatherSample(
                instant,
                lerp(left.windSpeedKmh(), right.windSpeedKmh(), t),
                from,
                flow,
                lerp(left.cloudCoverPercent(), right.cloudCoverPercent(), t),
                lerp(left.airTemperatureC(), right.airTemperatureC(), t),
                lerp(left.precipitationMm(), right.precipitationMm(), t),
                lerp(left.pressureHpa(), right.pressureHpa(), t),
                lerp(left.shortwaveRadiation(), right.shortwaveRadiation(), t),
                lerp(left.directRadiation(), right.directRadiation(), t)
        );
    }

    private static Double lerp(Double a, Double b, double t) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a + (b - a) * t;
    }

    private static Double lerpAngle(Double a, Double b, double t) {
        if (a == null) {
            return b == null ? null : AzimuthConvention.normalize(b);
        }
        if (b == null) {
            return AzimuthConvention.normalize(a);
        }
        return AzimuthConvention.normalize(a + t * AzimuthConvention.signedDelta(a, b));
    }

    private record TimedHour(Instant at, WeatherContext.HourlyWeather hour) {
    }
}
