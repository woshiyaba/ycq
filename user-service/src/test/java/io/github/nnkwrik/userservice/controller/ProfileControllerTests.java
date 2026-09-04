package io.github.nnkwrik.userservice.controller;

import io.github.nnkwrik.common.dto.JWTUser;
import io.github.nnkwrik.common.dto.UserProfile;
import io.github.nnkwrik.userservice.dao.ProfileMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class ProfileControllerTests {
    @Mock
    private ProfileMapper profiles;
    private ProfileController controller;

    @Before
    public void setUp() {
        controller = new ProfileController(profiles);
    }

    @Test
    public void savesOverrideForAuthenticatedUserInsteadOfSubmittedIdentity() {
        UserProfile profile = validProfile();
        profile.setOpenId("someone-else");
        when(profiles.get("signed-in-user")).thenReturn(profile);

        assertEquals(0, controller.update(new JWTUser("signed-in-user", "原微信昵称", ""), profile).getErrno());
        assertEquals("signed-in-user", profile.getOpenId());
        assertEquals("运城圈友", profile.getNickName());
        assertEquals("", profile.getBio());
        assertEquals("", profile.getAvatarUrl());
        assertEquals(Integer.valueOf(0), profile.getGender());
        verify(profiles).save(profile);
        verify(profiles, times(2)).get("signed-in-user");
        verifyNoMoreInteractions(profiles);
    }

    @Test
    public void rejectsMalformedFieldsWithReadableErrorsBeforeWriting() {
        invalid(() -> ProfileController.validate(null));
        UserProfile profile = validProfile();
        for (String url : Arrays.asList("javascript:alert(1)", "http:///missing-host", "https://user:secret@example.com/avatar", "https://bad host/a.png")) {
            profile.setAvatarUrl(url);
            invalid(() -> controller.update(new JWTUser("signed-in-user", "昵称", ""), profile));
        }
        profile.setAvatarUrl("https://example.com/avatar.png");
        profile.setBio(String.join("", Collections.nCopies(301, "长")));
        invalid(() -> ProfileController.validate(profile));
        profile.setBio("");
        profile.setGender(3);
        invalid(() -> ProfileController.validate(profile));
        profile.setGender(1);
        profile.setNickName("昵称\n换行");
        invalid(() -> ProfileController.validate(profile));
        verifyZeroInteractions(profiles);
    }

    private UserProfile validProfile() {
        UserProfile profile = new UserProfile();
        profile.setNickName(" 运城圈友 ");
        return profile;
    }

    private void invalid(Runnable action) {
        try {
            action.run();
            fail("Invalid profile must be rejected");
        } catch (IllegalArgumentException error) {
            assertNotNull(error.getMessage());
            assertFalse(error.getMessage().trim().isEmpty());
        }
    }
}
