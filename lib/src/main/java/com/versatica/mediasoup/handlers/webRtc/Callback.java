package com.versatica.mediasoup.handlers.webRtc;

public interface Callback {

    /**
     * Schedule function execution represented by this {@link Callback} instance
     *
     * @param args arguments passed to callback method
     */
    public void invoke(Object... args);

}
