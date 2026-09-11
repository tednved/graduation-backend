package com.graduation.backend.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 契约标量的全局序列化规则。集中在这里而不是逐个 DTO 标注，避免漏掉某个字段时
 * 悄悄产出违反契约的 JSON。
 *
 * <ul>
 *   <li>ID：{@code Long}/{@code long} 一律输出十进制字符串，避免微信 JavaScript 丢失 BIGINT 精度；</li>
 *   <li>金额与平均分：{@code BigDecimal} 输出两位小数字符串；</li>
 *   <li>时间：{@code Instant} 输出 UTC ISO 8601，固定三位毫秒并以 {@code Z} 结尾。</li>
 * </ul>
 *
 * 计数类字段（分页、版本号、条数）在 DTO 中使用 {@code Integer} 声明，因此不会被 ID 规则命中。
 */
@Configuration
public class JacksonConfig {

    public static final DateTimeFormatter UTC_MILLIS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    @Bean
    public SimpleModule contractScalarModule() {
        SimpleModule module = new SimpleModule("contractScalarModule");
        LongStringSerializer longAsString = new LongStringSerializer();
        module.addSerializer(Long.class, longAsString);
        module.addSerializer(Long.TYPE, longAsString);
        module.addSerializer(BigDecimal.class, new MoneyStringSerializer());
        module.addSerializer(Instant.class, new UtcInstantSerializer());
        return module;
    }

    static final class LongStringSerializer extends ValueSerializer<Long> {
        @Override
        public void serialize(Long value, JsonGenerator generator, SerializationContext context) {
            generator.writeString(value.toString());
        }
    }

    static final class MoneyStringSerializer extends ValueSerializer<BigDecimal> {
        @Override
        public void serialize(BigDecimal value, JsonGenerator generator, SerializationContext context) {
            generator.writeString(value.setScale(2).toPlainString());
        }
    }

    static final class UtcInstantSerializer extends ValueSerializer<Instant> {
        @Override
        public void serialize(Instant value, JsonGenerator generator, SerializationContext context) {
            generator.writeString(UTC_MILLIS.format(value));
        }
    }
}
