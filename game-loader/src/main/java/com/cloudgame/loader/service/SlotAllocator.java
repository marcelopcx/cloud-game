package com.cloudgame.loader.service;

import com.cloudgame.loader.config.LoaderProperties;
import com.cloudgame.loader.exception.LoaderException;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Slot {@code 0..maxContainers-1} de cada estación. Con host network dos
 * estaciones no pueden compartir puerto HTTP ni display X:
 * {@code puerto = basePort + slot}, {@code display = :(baseDisplay + slot)}.
 * No guarda estado: los slots ocupados salen de las labels de los contenedores vivos.
 */
@Component
public class SlotAllocator {

    private final LoaderProperties props;

    public SlotAllocator(LoaderProperties props) {
        this.props = props;
    }

    /** Slot libre más bajo; {@code CAPACITY_EXCEEDED} si no queda ninguno. */
    public int allocate(Collection<Integer> used) {
        Set<Integer> taken = used.stream().collect(Collectors.toSet());
        for (int slot = 0; slot < props.getMaxContainers(); slot++) {
            if (!taken.contains(slot)) {
                return slot;
            }
        }
        throw LoaderException.capacityExceeded(props.getMaxContainers());
    }

    public int port(int slot) {
        return props.getBasePort() + slot;
    }

    public String display(int slot) {
        return ":" + (props.getBaseDisplay() + slot);
    }
}
