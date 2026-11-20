package api.dto;

import java.util.List;

/** Distribución del home: las secciones en el orden en que se muestran. Vacía = sin configurar. */
public record HomeResponse(List<HomeSectionResponse> sections) {
}
