package co.entomo.gdhcn.entity;/**
 * @author Uday Matta
 */

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * @author Uday Matta
 * @organization Entomo Labs
 */
@Data
@Entity
@Table(name = "ips_file")
public class IpsFile
{
    @Id
    @GeneratedValue
    @UuidGenerator
    String id;

    @CreationTimestamp
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at")
    Instant createdAt;
    @Column(nullable = false)
    private String manifestId;

    @Column(columnDefinition = "boolean default false")
    private boolean isAccessed;
}
