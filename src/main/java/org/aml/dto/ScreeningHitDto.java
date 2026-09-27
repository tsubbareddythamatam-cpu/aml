package org.aml.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreeningHitDto {
    private String hitName;
    private String category;
    private String source;
    private Integer score;
    private String hitDetermination;
    private String comments;
}
