package com.appointmentprep.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpTest {

  @Test
  void usesTheLastForwardedForEntryWhichOurProxyAdded() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("172.18.0.5");
    request.addHeader("X-Forwarded-For", "10.0.0.1, 1.2.3.4 ,  203.0.113.9 ");

    assertThat(ClientIp.of(request)).isEqualTo("203.0.113.9");
  }

  @Test
  void fallsBackToTheConnectionAddress() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("203.0.113.9");

    assertThat(ClientIp.of(request)).isEqualTo("203.0.113.9");

    request.addHeader("X-Forwarded-For", "1.2.3.4, ");
    assertThat(ClientIp.of(request)).isEqualTo("203.0.113.9");
  }
}
