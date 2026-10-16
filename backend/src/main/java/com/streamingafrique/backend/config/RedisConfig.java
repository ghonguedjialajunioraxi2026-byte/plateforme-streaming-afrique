package com.streamingafrique.backend.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Configuration Redis du backend.
 *
 * La connexion technique (Lettuce) est automatiquement créée par Spring Boot
 * depuis les propriétés {@code spring.data.redis.*} (host, port, password, timeout, ssl).
 *
 * Le template String → String est fourni automatiquement par Spring Boot
 * via {@code StringRedisTemplate} — pas de duplication ici.
 *
 * On définit :
 *  - le serializer JSON ({@link GenericJackson2JsonRedisSerializer}) qui
 *    conserve le type d'origine (@class) pour restaurer exactement nos DTOs
 *    (records) ET gère les dates {@code java.time.*} (Instant…) via le
 *    {@link JavaTimeModule} — indispensable pour les DTOs de cache qui
 *    portent des horodatages (createdAt, publishedAt, measuredAt…).
 *  - le template {@code redisObjectTemplate} (String → Object sérialisé en
 *    JSON) pour les données plus complexes.
 *
 * Évolution future : ajouter un {@code ConnectionFactory} custom pour
 * Sentinel, Cluster, ou pool de connexions.
 */
@Configuration
@EnableConfigurationProperties(RedisCacheProperties.class)
public class RedisConfig {

    /**
     * Sérialiseur JSON des valeurs de cache : mêmes réglages que le
     * défaut de Spring Data Redis (typage {@code @class} pour recréer nos
     * DTOs), complété par le support des dates {@code java.time.*}.
     */
    @Bean
    public GenericJackson2JsonRedisSerializer redisJsonSerializer() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // Null values : marqueur Redis (comme le défaut de Spring Data Redis).
        GenericJackson2JsonRedisSerializer.registerNullValueSerializer(mapper, null);
        // Enregistre le type d'origine dans le JSON ("@class") pour pouvoir
        // restaurer exactement les records (DTOs) stockés en cache.
        mapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.EVERYTHING, JsonTypeInfo.As.PROPERTY);
        return new GenericJackson2JsonRedisSerializer(mapper);
    }

    /**
     * Template String → Object (JSON).
     * Utilise {@link #redisJsonSerializer()} pour sérialiser automatiquement
     * les objets Java en JSON et les restaurer.
     */
    @Bean
    public RedisTemplate<String, Object> redisObjectTemplate(RedisConnectionFactory connectionFactory,
                                                            GenericJackson2JsonRedisSerializer redisJsonSerializer) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(redisJsonSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(redisJsonSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
