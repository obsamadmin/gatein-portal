/*
 * Copyright (C) 2021 eXo Platform SAS.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.gatein.security.oauth.openid;

import org.apache.commons.lang3.StringUtils;
import org.gatein.security.oauth.spi.OAuthPrincipal;
import org.gatein.security.oauth.spi.OAuthPrincipalProcessor;
import org.gatein.security.oauth.utils.OAuthUtils;

import org.exoplatform.services.organization.User;
import org.exoplatform.services.organization.impl.UserImpl;

public class OpenIdPrincipalProcessor implements OAuthPrincipalProcessor {
    @Override
    public User convertToGateInUser(OAuthPrincipal principal) {
        String email = principal.getEmail();
        String username = principal.getUserName();
        UserImpl gateinUser = new UserImpl();
        if(StringUtils.isNotBlank(username)) {
          gateinUser.setUserName(OAuthUtils.refineUserName(username));
        }
        gateinUser.setFirstName(principal.getFirstName());
        gateinUser.setLastName(principal.getLastName());
        gateinUser.setEmail(email);
        if(StringUtils.isNotBlank(principal.getDisplayName())) {
          gateinUser.setDisplayName(principal.getDisplayName());
        }

        return gateinUser;
    }
}
