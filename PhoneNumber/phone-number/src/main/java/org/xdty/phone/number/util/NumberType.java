package org.xdty.phone.number.util;

/**
 * 电话号码类型分类。
 *
 * <p>用于后续数据源路由：不同号码类型走不同的检索列 / 数据源。</p>
 *
 * <ul>
 *   <li>{@link #CN_MOBILE}  大陆手机号：11 位，1[3-9] 开头</li>
 *   <li>{@link #CN_FIXED}   大陆座机：带 0 区号(3~4位) + 7~8 位号码</li>
 *   <li>{@link #CN_SPECIAL} 国内特服/服务号：10086、400、800、110、119、12345 等</li>
 *   <li>{@link #INTL}       国际号码（非 +86/0086 前缀）</li>
 *   <li>{@link #UNKNOWN}    无法归类</li>
 * </ul>
 */
public enum NumberType {
    CN_MOBILE,
    CN_FIXED,
    CN_SPECIAL,
    INTL,
    UNKNOWN
}
