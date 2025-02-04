/*
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2022 Meeds Association contact@meeds.io
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.exoplatform.web.login.recovery;

import org.exoplatform.services.organization.User;

import org.gatein.wci.security.Credentials;

import javax.servlet.http.HttpServletRequest;

import java.util.Locale;

/**
 * @author <a href="mailto:tuyennt@exoplatform.com">Tuyen Nguyen The</a>.
 */
public interface PasswordRecoveryService {
  void addConnector(ChangePasswordConnector connector);

  Credentials verifyToken(String tokenId, String type);

  Credentials verifyToken(String tokenId);

  boolean changePass(final String tokenId, final String tokenType, final String username, final String password);

  public boolean sendRecoverPasswordEmail(User user, Locale defaultLocale, HttpServletRequest req);

  public boolean sendOnboardingEmail(User user, Locale defaultLocale, StringBuilder url);

  public String sendExternalRegisterEmail(String sender,
                                          String email,
                                          Locale locale,
                                          String space,
                                          StringBuilder url) throws Exception;

  public boolean sendExternalConfirmationAccountEmail(String sender, Locale locale, StringBuilder url);

  public boolean allowChangePassword(String username) throws Exception;
  // EXOGTN-2114 Workaround for Java 8 Backward compatibility.

  String getPasswordRecoverURL(String tokenId, String lang);

  String getOnboardingURL(String tokenId, String lang);

  String getExternalRegistrationURL(String tokenId, String lang);

  public ChangePasswordConnector getActiveChangePasswordConnector();
}
