package com.todo.global.controller;

import com.todo.global.config.AppleProperties;
import com.todo.global.dto.response.AppleAppSiteAssociationResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class WellKnownControllerTest {

    @Mock
    private AppleProperties appleProperties;

    @Test
    void appID를_teamId와_iosClientId를_조합해_생성한다() {
        WellKnownController controller = new WellKnownController(appleProperties);
        given(appleProperties.teamId()).willReturn("ABCDE12345");
        given(appleProperties.iosClientId()).willReturn("org.bluerack.todo");

        AppleAppSiteAssociationResponse response = controller.getAppleAppSiteAssociation();

        assertThat(response.applinks().details()).singleElement().satisfies(detail -> {
            assertThat(detail.appID()).isEqualTo("ABCDE12345.org.bluerack.todo");
            assertThat(detail.paths()).containsExactly("/invite*");
        });
        assertThat(response.applinks().apps()).isEmpty();
    }
}
