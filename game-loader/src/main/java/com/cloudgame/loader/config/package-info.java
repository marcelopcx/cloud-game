/**
 * Configuración de arranque.
 *
 * <p>{@link com.cloudgame.loader.config.LoaderProperties} liga el prefijo
 * {@code loader.*} de {@code application.yml}. {@link com.cloudgame.loader.config.DockerClientConfig}
 * arma los dos clientes de Docker (corto y streaming) y
 * {@link com.cloudgame.loader.config.ApiTokenFilter} exige el Bearer en toda
 * ruta salvo {@code /health}.
 */
package com.cloudgame.loader.config;
