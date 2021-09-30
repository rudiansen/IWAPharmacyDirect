/*
        Insecure Web App (IWA)

        Copyright (C) 2020 Micro Focus or one of its affiliates

        This program is free software: you can redistribute it and/or modify
        it under the terms of the GNU General Public License as published by
        the Free Software Foundation, either version 3 of the License, or
        (at your option) any later version.

        This program is distributed in the hope that it will be useful,
        but WITHOUT ANY WARRANTY; without even the implied warranty of
        MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
        GNU General Public License for more details.

        You should have received a copy of the GNU General Public License
        along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package com.microfocus.example.utils;

import com.microfocus.example.entity.Authority;
import com.microfocus.example.entity.User;
import org.owasp.esapi.ESAPI;
import org.springframework.security.core.Authentication;

import java.security.Principal;
import java.util.Set;

import org.owasp.esapi.ESAPI.*;

/**
 * Customer Web Utilities
 *
 * @author Kevin. A. Lee
 */
public class WebUtils {

    public static String toString(User user) {
        StringBuilder sb = new StringBuilder();

        sb.append("UserName:").append(user.getUsername());

        Set<Authority> authorities = user.getAuthorities();
        if (authorities != null && !authorities.isEmpty()) {
            sb.append(" (");
            boolean first = true;
            for (Authority a : authorities) {
                if (first) {
                    sb.append(a.getName());
                    first = false;
                } else {
                    sb.append(", ").append(a.getName());
                }
            }
            sb.append(")");
        }
        return sb.toString();
    }

    public static User getLoggedInUser(Principal principal) {
        User loggedInUser = null;
        if (principal != null) {
            org.springframework.security.core.userdetails.UserDetails user =
                    (org.springframework.security.core.userdetails.UserDetails) ((Authentication) principal).getPrincipal();
           loggedInUser = User.fromUserDetails(user);
        }
        return loggedInUser;
    }

    /**
     * Utility method for sanitizing a String to neutralize any possible malicious content. This is used primarily to protect log
     * messages by encoding for any possible forgery or injection attempts.
     *
     * Given an Object of type Integer or Long, converts the Object instance to a Long.  This will throw a ClassCastException
     * if the past parameter is not either an Integer or a Long.
     *
     * @param string
     * @return String
     */
    public static String sanitize(String string) {
        if (string == null) {
            return "NULL";
        }
        String sanitized = string.replace('\n', '_').replace('\r', '_');
        return ESAPI.encoder().encodeForHTML(sanitized);
    }

}
