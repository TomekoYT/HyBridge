package tomeko.hybridge.event;

//? if 1.8.9 {
/*public final class ClientTickEvents {
    private ClientTickEvents() {}

    public static final Event<Start> START_CLIENT_TICK = Event.create(Start.class, listeners -> minecraft -> {
        for (Start listener : listeners) {
            listener.onStartTick(minecraft);
        }
    });

    public static final Event<End> END_CLIENT_TICK = Event.create(End.class, listeners -> minecraft -> {
        for (End listener : listeners) {
            listener.onEndTick(minecraft);
        }
    });

    @FunctionalInterface
    public interface Start {
        void onStartTick(net.minecraft.client.Minecraft minecraft);
    }

    @FunctionalInterface
    public interface End {
        void onEndTick(net.minecraft.client.Minecraft minecraft);
    }
}
*///?}