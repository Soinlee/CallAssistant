package org.xdty.phone.number.model.area;

import android.text.TextUtils;

import org.xdty.phone.number.model.INumber;
import org.xdty.phone.number.model.Type;

/**
 * 国际号码国家识别结果。
 *
 * <p>承载 area_code.db 中命中的国家/地区名，作为归属地信息显示。</p>
 */
public class AreaCodeNumber implements INumber {

    private final String number;
    private final String countryName;

    public AreaCodeNumber(String number, String countryName) {
        this.number = number;
        this.countryName = countryName == null ? "" : countryName.trim();
    }

    @Override
    public String getName() {
        return "";
    }

    @Override
    public String getProvince() {
        return "";
    }

    @Override
    public Type getType() {
        return Type.NORMAL;
    }

    @Override
    public String getCity() {
        // 国家/地区名作为归属地显示
        return countryName;
    }

    @Override
    public String getNumber() {
        return number;
    }

    @Override
    public String getProvider() {
        return "";
    }

    @Override
    public int getCount() {
        return 0;
    }

    @Override
    public boolean isValid() {
        return !TextUtils.isEmpty(number);
    }

    @Override
    public boolean isOnline() {
        return false;
    }

    @Override
    public boolean hasGeo() {
        return !TextUtils.isEmpty(countryName);
    }

    @Override
    public int getApiId() {
        return INumber.API_ID_AREA;
    }

    @Override
    public void patch(INumber i) {
        // no-op：国家识别为独立结果
    }
}
