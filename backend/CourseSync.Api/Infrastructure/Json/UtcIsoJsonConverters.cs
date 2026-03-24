using System.Globalization;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace CourseSync.Api.Infrastructure.Json;

public sealed class UtcIsoDateTimeConverter : JsonConverter<DateTime>
{
    public override DateTime Read(ref Utf8JsonReader reader, Type typeToConvert, JsonSerializerOptions options)
    {
        if (reader.TokenType != JsonTokenType.String)
            throw new JsonException("Expected string for DateTime.");

        var s = reader.GetString();
        if (string.IsNullOrEmpty(s))
            throw new JsonException("DateTime string is empty.");

        if (DateTimeOffset.TryParse(s, CultureInfo.InvariantCulture, DateTimeStyles.RoundtripKind, out var dto))
            return DateTime.SpecifyKind(dto.UtcDateTime, DateTimeKind.Utc);

        if (DateTime.TryParse(
                s,
                CultureInfo.InvariantCulture,
                DateTimeStyles.AssumeUniversal | DateTimeStyles.AdjustToUniversal,
                out var utcAssumed))
            return DateTime.SpecifyKind(utcAssumed, DateTimeKind.Utc);

        if (DateOnly.TryParse(s, CultureInfo.InvariantCulture, DateTimeStyles.None, out var dateOnly))
            return DateTime.SpecifyKind(dateOnly.ToDateTime(TimeOnly.MinValue), DateTimeKind.Utc);

        throw new JsonException($"Invalid DateTime: {s}");
    }

    public override void Write(Utf8JsonWriter writer, DateTime value, JsonSerializerOptions options)
    {
        var utc = value.Kind switch
        {
            DateTimeKind.Utc => value,
            DateTimeKind.Local => value.ToUniversalTime(),
            _ => DateTime.SpecifyKind(value, DateTimeKind.Utc)
        };
        writer.WriteStringValue(utc.ToString("O", CultureInfo.InvariantCulture));
    }
}

public sealed class UtcIsoDateTimeOffsetConverter : JsonConverter<DateTimeOffset>
{
    public override DateTimeOffset Read(ref Utf8JsonReader reader, Type typeToConvert, JsonSerializerOptions options)
    {
        if (reader.TokenType != JsonTokenType.String)
            throw new JsonException("Expected string for DateTimeOffset.");

        var s = reader.GetString();
        if (string.IsNullOrEmpty(s))
            throw new JsonException("DateTimeOffset string is empty.");

        if (DateTimeOffset.TryParse(s, CultureInfo.InvariantCulture, DateTimeStyles.RoundtripKind, out var dto))
            return dto;

        if (DateTime.TryParse(
                s,
                CultureInfo.InvariantCulture,
                DateTimeStyles.AssumeUniversal | DateTimeStyles.AdjustToUniversal,
                out var dt))
            return new DateTimeOffset(DateTime.SpecifyKind(dt, DateTimeKind.Utc), TimeSpan.Zero);

        if (DateOnly.TryParse(s, CultureInfo.InvariantCulture, DateTimeStyles.None, out var dateOnly))
            return new DateTimeOffset(dateOnly.ToDateTime(TimeOnly.MinValue), TimeSpan.Zero);

        throw new JsonException($"Invalid DateTimeOffset: {s}");
    }

    public override void Write(Utf8JsonWriter writer, DateTimeOffset value, JsonSerializerOptions options)
    {
        var utc = value.ToUniversalTime();
        writer.WriteStringValue(utc.ToString("yyyy-MM-dd'T'HH:mm:ss.fffZ", CultureInfo.InvariantCulture));
    }
}
