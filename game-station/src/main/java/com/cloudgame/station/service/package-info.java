/**
 * Casos de uso de la estación.
 *
 * <p>{@link com.cloudgame.station.service.StationService} es la FSM y el único
 * escritor de estado. {@link com.cloudgame.station.service.GameCatalog} resuelve
 * el manifiesto (classpath {@code manifest.yaml} si no hay
 * {@code station.game-manifest}). {@link com.cloudgame.station.service.FilePreparer}
 * copia a cache. {@link com.cloudgame.station.service.GameRuntime} levanta Xvfb
 * y el binario. Este corte no levanta Pulse aunque {@code needs.audio}
 * esté activo. {@link com.cloudgame.station.service.InputSink} aplica el
 * datagrama. {@link com.cloudgame.station.service.WebrtcMediaSession} implementa
 * {@link com.cloudgame.station.service.MediaSession}: el browser es offerer,
 * la estación responde SDP y empuja I420 (ffmpeg {@code x11grab} o barras).
 *
 * <p>La factory nativa de webrtc-java se crea en {@code startSource}, no en
 * el arranque: {@code GET /health} no carga los .so.
 */
package com.cloudgame.station.service;
