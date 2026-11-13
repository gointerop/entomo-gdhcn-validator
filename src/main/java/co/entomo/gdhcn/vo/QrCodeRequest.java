package co.entomo.gdhcn.vo;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
/**
 * @author Uday Matta
 * @organization entomo Labs
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QrCodeRequest {

	private String passCode;
	@JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
	private Instant expiresOn;
	@NotBlank(message = "jsonContent is mandatory")
	private String jsonContent;
}
