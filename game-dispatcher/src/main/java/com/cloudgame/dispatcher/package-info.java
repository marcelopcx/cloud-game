/**
 * Game Dispatcher: único punto de entrada para pedir una partida. Reutiliza
 * estaciones libres, pide al Game Loader las que faltan (reservadas para quien
 * las pidió), mantiene el mínimo por juego y libera las inactivas. El estado
 * vive en memoria y se reconstruye al arrancar desde el loader y el
 * {@code /health} de cada estación.
 */
package com.cloudgame.dispatcher;
