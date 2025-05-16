package io.flamingock.core.context;

import io.flamingock.commons.utils.Property;

import java.io.File;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.ZonedDateTime;
import java.util.Currency;
import java.util.Locale;
import java.util.UUID;

public interface PropertyInjectable {

    void setProperty(Property value);

    void setProperty(String key, String value);

    void setProperty(String key, Boolean value);

    void setProperty(String key, Integer value);

    void setProperty(String key, Float value);

    void setProperty(String key, Long value);

    void setProperty(String key, Double value);

    void setProperty(String key, UUID value);

    void setProperty(String key, Currency value);

    void setProperty(String key, Locale value);

    void setProperty(String key, Charset value);

    void setProperty(String key, File value);

    void setProperty(String key, Path value);

    void setProperty(String key, InetAddress value);

    void setProperty(String key, URL value);

    void setProperty(String key, URI value);

    void setProperty(String key, Duration value);

    void setProperty(String key, Period value);

    void setProperty(String key, Instant value);

    void setProperty(String key, LocalDate value);

    void setProperty(String key, LocalTime value);

    void setProperty(String key, LocalDateTime value);

    void setProperty(String key, ZonedDateTime value);

    void setProperty(String key, OffsetDateTime value);

    void setProperty(String key, OffsetTime value);

    void setProperty(String key, java.util.Date value);

    void setProperty(String key, java.sql.Date value);

    void setProperty(String key, Time value);

    void setProperty(String key, Timestamp value);

    void setProperty(String key, String[] value);

    void setProperty(String key, Integer[] value);

    void setProperty(String key, Long[] value);

    void setProperty(String key, Double[] value);

    void setProperty(String key, Float[] value);

    void setProperty(String key, Boolean[] value);

    void setProperty(String key, Byte[] value);

    void setProperty(String key, Short[] value);

    void setProperty(String key, Character[] value);

    <T extends Enum<T>> void setProperty(String key, T value);
}
