/*
 * Symphony - A modern community (forum/BBS/SNS/blog) platform written in Java.
 * Copyright (C) 2012-2018, b3log.org & hacpai.com
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.b3log.symphony.util;

import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;
import org.b3log.latke.Keys;
import org.b3log.latke.Latkes;
import org.b3log.latke.logging.Level;
import org.b3log.latke.logging.Logger;
import org.b3log.latke.model.User;
import org.b3log.latke.util.Crypts;
import org.b3log.latke.util.Requests;
import org.b3log.symphony.model.Common;
import org.json.JSONObject;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * Session utilities.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 2.0.3.1, Sep 1, 2018
 */
public final class Sessions {

    /**
     * Cookie name.
     */
    public static final String COOKIE_NAME = "b3log-latke";

    /**
     * Logger.
     */
    private static final Logger LOGGER = Logger.getLogger(Sessions.class);

    /**
     * Cookie expiry: 30 days.
     */
    private static final int COOKIE_EXPIRY = 60 * 60 * 24 * 30;

    /**
     * Private constructor.
     */
    private Sessions() {
    }

    /**
     * Gets CSRF token from the specified request.
     *
     * @param request the specified request
     * @return CSRF token, returns {@code ""} if not found
     */
    public static String getCSRFToken(final HttpServletRequest request) {
        final HttpSession session = request.getSession(false);

        if (null == session) {
            return "";
        }

        final String ret = (String) session.getAttribute(Common.CSRF_TOKEN);
        if (StringUtils.isBlank(ret)) {
            return "";
        }

        return ret;
    }

    /**
     * Logins the specified user from the specified request.
     * <p>
     * If no session of the specified request, do nothing.
     * </p>
     *
     * @param request       the specified request
     * @param response      the specified response
     * @param user          the specified user, for example,
     *                      "oId": "",
     *                      "userPassword": ""
     * @param rememberLogin remember login or not
     * @return token, returns {@code null} if login failed
     */
    public static String login(final HttpServletRequest request, final HttpServletResponse response,
                               final JSONObject user, final boolean rememberLogin) {
        final HttpSession session = request.getSession(false);

        if (null == session) {
            LOGGER.warn("The session is null");

            return null;
        }

        session.setAttribute(User.USER, user);
        request.setAttribute(Common.IP, Requests.getRemoteAddr(request));
        session.setAttribute(Common.CSRF_TOKEN, RandomStringUtils.randomAlphanumeric(12));

        try {
            final JSONObject cookieJSONObject = new JSONObject();

            cookieJSONObject.put(Keys.OBJECT_ID, user.optString(Keys.OBJECT_ID));

            final String random = RandomStringUtils.random(16);
            cookieJSONObject.put(Keys.TOKEN, user.optString(User.USER_PASSWORD) + ":" + random);
            cookieJSONObject.put(Common.REMEMBER_LOGIN, rememberLogin);

            final String ret = Crypts.encryptByAES(cookieJSONObject.toString(), Symphonys.get("cookie.secret"));
            final Cookie cookie = new Cookie(COOKIE_NAME, ret);

            cookie.setPath("/");
            cookie.setMaxAge(rememberLogin ? COOKIE_EXPIRY : -1);
            cookie.setHttpOnly(true); // HTTP Only
            cookie.setSecure(StringUtils.equalsIgnoreCase(Latkes.getServerScheme(), "https"));

            response.addCookie(cookie);

            return ret;
        } catch (final Exception e) {
            LOGGER.log(Level.WARN, "Can not write cookie [oId=" + user.optString(Keys.OBJECT_ID)
                    + ", token=" + user.optString(User.USER_PASSWORD) + "]");

            return null;
        }
    }

    /**
     * Logouts a user with the specified request.
     *
     * @param request  the specified request
     * @param response the specified response
     * @return {@code true} if succeed, otherwise returns {@code false}
     */
    public static boolean logout(final HttpServletRequest request, final HttpServletResponse response) {
        final HttpSession session = request.getSession(false);

        if (null != session) {
            final Cookie cookie = new Cookie(COOKIE_NAME, null);

            cookie.setMaxAge(0);
            cookie.setPath("/");

            response.addCookie(cookie);

            session.invalidate();

            return true;
        }

        return false;
    }

    /**
     * Gets the current user with the specified request.
     *
     * @param request the specified request
     * @return the current user, returns {@code null} if not logged in
     */
    public static JSONObject currentUser(final HttpServletRequest request) {
        final HttpSession session = request.getSession(false);

        if (null != session) {
            return (JSONObject) session.getAttribute(User.USER);
        }

        return null;
    }
}
