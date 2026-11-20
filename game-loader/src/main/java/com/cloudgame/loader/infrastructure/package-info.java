/**
 * Acceso a Docker. Todo pasa por {@link com.cloudgame.loader.infrastructure.ContainerRuntime}
 * para que los tests no necesiten un daemon; {@link com.cloudgame.loader.infrastructure.DockerContainerRuntime}
 * es la implementación real con docker-java y
 * {@link com.cloudgame.loader.infrastructure.StationContainerSpec} traduce un pedido
 * a labels + env con la misma forma que {@code compose.yaml}.
 */
package com.cloudgame.loader.infrastructure;
