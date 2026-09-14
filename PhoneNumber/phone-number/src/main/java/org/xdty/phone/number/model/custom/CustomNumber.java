package org.xdty.phone.number.model.custom;

import android.text.TextUtils;

import org.xdty.phone.number.model.INumber;
import org.xdty.phone.number.model.Type;

/**
 * 自定义数据源(SQLite 归属地库)命中结果。
 * 承载省份 / 城市 / 运营商三字段归属地信息。
 */
public class CustomNumber implements INumber {

    private final String number;
    private final String province;
    private final String city;
    private final String isp;

    public CustomNumber(String number, String province, String city, String isp) {
        this.number = number;
        this.province = province == null ? "" : province.trim();
        this.city = city == null ? "" : city.trim();
        this.isp = isp == null ? "" : isp.trim();
    }

    @Override
    public String getName() {
        return "";
    }

    @Override
    public String getProvince() {
        return province;
    }

    @Override
    public Type getType() {
        return Type.NORMAL;
    }

    @Override
    public String getCity() {
        return city;
    }

    @Override
    public String getNumber() {
        return number;
    }

    @Override
    public String getProvider() {
        return isp;
    }

    @Override
    public int getCount() {
        return 0;
    }

    @Override
    public boolean isValid() {
        return !TextUtils.isEmpty(getNumber());
    }

    @Override
    public boolean isOnline() {
        return false;
    }

    @Override
    public boolean hasGeo() {
        return !TextUtils.isEmpty(getProvince())
                || !TextUtils.isEmpty(getCity())
                || !TextUtils.isEmpty(getProvider());
    }

    @Override
    public int getApiId() {
        return INumber.API_ID_CUSTOM;
    }

    @Override
    public void patch(INumber i) {
        // no-op: 自定义数据源已是首选归属地源，无需补丁
    }
}
