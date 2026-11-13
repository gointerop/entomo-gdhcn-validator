package co.entomo.gdhcn.vo;

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
public class ManifestRequest {
    private String recipient;
    private String passcode;
}
