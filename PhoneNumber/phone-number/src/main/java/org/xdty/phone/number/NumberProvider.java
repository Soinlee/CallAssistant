package org.xdty.phone.number;

import android.content.Context;

import org.xdty.phone.number.model.NumberHandler;
import org.xdty.phone.number.model.area.AreaCodeHandler;
import org.xdty.phone.number.model.caller.CallerHandler;
import org.xdty.phone.number.model.common.CommonHandler;
import org.xdty.phone.number.model.custom.CustomHandler;
import org.xdty.phone.number.model.google.GoogleNumberHandler;
import org.xdty.phone.number.model.marked.MarkedHandler;
import org.xdty.phone.number.model.mvno.MvnoHandler;
import org.xdty.phone.number.model.offline.OfflineHandler;
import org.xdty.phone.number.model.special.SpecialNumberHandler;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class NumberProvider {
    private final static Map<Integer, NumberHandler> OFFLINE_PROVIDERS = new HashMap<>();

    public static void init(Context context) {

        registerOffline(new CustomHandler(context));
        registerOffline(new AreaCodeHandler(context));
        registerOffline(new SpecialNumberHandler(context));
        registerOffline(new CommonHandler(context));
        registerOffline(new CallerHandler(context));
        registerOffline(new MarkedHandler(context));
        registerOffline(new OfflineHandler(context));
        registerOffline(new MvnoHandler(context));
        registerOffline(new GoogleNumberHandler(context));
    }

    public static void registerOffline(NumberHandler handler) {
        OFFLINE_PROVIDERS.put(handler.getApiId(), handler);
    }

    public static void clear() {
        OFFLINE_PROVIDERS.clear();
    }

    public static Collection<NumberHandler> providers() {
        return OFFLINE_PROVIDERS.values();
    }

    public static int size() {
        return OFFLINE_PROVIDERS.size();
    }
}
