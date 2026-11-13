package co.entomo.gdhcn.entity;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 *  @author Uday Matta
 *  @organization entomo Labs
 */
@Data
@Entity
@Table(name = "qr_code")
public class QrCode {

	@Id
	@Column(length = 36)
	private String id;
	
	private String passCode;
	
	@JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
	@JdbcTypeCode(SqlTypes.TIMESTAMP)
	private Instant expiresOn;
	
	@CreationTimestamp
	@JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
	@JdbcTypeCode(SqlTypes.TIMESTAMP)
	@Column(name = "created_at")
    private Instant createdAt;
	private String countryCode;
	private String jsonUrl;
	private String flag;
	private String key;

	/*
	@Column(columnDefinition = "boolean default false")
	private boolean isAccessed;
	@JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
	@JdbcTypeCode(SqlTypes.TIMESTAMP)
	@Column(name = "manifest_created_at")
	private Instant manifestCreatedAt;
	 */

	@Column(nullable = false)
	private String manifestId;
}
