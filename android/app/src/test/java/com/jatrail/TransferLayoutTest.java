package com.jatrail;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.text.method.PasswordTransformationMethod;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import com.google.android.material.textfield.TextInputLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class TransferLayoutTest {
    @Test
    public void uploadFormInflatesWithHeaderAndMaterialFields() {
        View form = inflater().inflate(R.layout.activity_transfer, null);
        EditText serverUrl = form.findViewById(R.id.edit_server_url);

        assertNotNull(form.findViewById(R.id.text_upload_header));
        assertNotNull(form.findViewById(R.id.input_trail_name));
        assertNotNull(form.findViewById(R.id.input_server_url));
        assertNotNull(form.findViewById(R.id.text_http_warning));
        assertNotNull(form.findViewById(R.id.button_use_official_server));
        assertNotNull(form.findViewById(R.id.button_send));
        assertEquals("https://jatrail.com/api/v1/trails",
                serverUrl.getText().toString());
    }

    @Test
    public void passwordIsMaskedAndHasVisibilityToggle() {
        View form = inflater().inflate(R.layout.activity_transfer, null);
        EditText password = form.findViewById(R.id.edit_password);
        TextInputLayout passwordLayout = form.findViewById(R.id.input_password);

        assertTrue(password.getTransformationMethod() instanceof PasswordTransformationMethod);
        assertEquals(TextInputLayout.END_ICON_PASSWORD_TOGGLE,
                passwordLayout.getEndIconMode());
    }

    private LayoutInflater inflater() {
        ContextThemeWrapper context = new ContextThemeWrapper(
                RuntimeEnvironment.getApplication(), R.style.AppTheme);
        return LayoutInflater.from(context);
    }
}
