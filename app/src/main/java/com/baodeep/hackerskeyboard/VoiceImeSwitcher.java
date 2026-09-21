// SPDX-License-Identifier: Apache-2.0
package com.baodeep.hackerskeyboard;

import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.IBinder;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

import java.util.List;

/** Provider-neutral voice-IME route. Does not record audio or handle recognized text. */
final class VoiceImeSwitcher {
    enum Result { UNAVAILABLE, REQUESTED, FAILED }

    interface Backend {
        List<InputMethodInfo> enabledMethods();
        List<InputMethodSubtype> enabledSubtypes(InputMethodInfo method);
        void switchTo(String id, InputMethodSubtype subtype);
    }

    private final Backend backend;
    private final String ownPackage;

    VoiceImeSwitcher(InputMethodService service) {
        this(new AndroidBackend(service), service.getPackageName());
    }

    VoiceImeSwitcher(Backend backend, String ownPackage) {
        this.backend = backend;
        this.ownPackage = ownPackage;
    }

    Result start() {
        try {
            // Re-read on every explicit user request: never keep a removed/disabled provider.
            // Keep platform order when several enabled providers expose voice subtypes.
            for (InputMethodInfo method : backend.enabledMethods()) {
                if (ownPackage.equals(method.getPackageName())) {
                    continue;
                }
                for (InputMethodSubtype subtype : backend.enabledSubtypes(method)) {
                    if ("voice".equals(subtype.getMode())) {
                        backend.switchTo(method.getId(), subtype);
                        // A void platform call confirms a request, not successful dictation.
                        return Result.REQUESTED;
                    }
                }
            }
            return Result.UNAVAILABLE;
        } catch (SecurityException | IllegalArgumentException exception) {
            // Provider removal or loss of the active IME token must not crash typing.
            // Do not silently redirect the user's voice input to another provider.
            return Result.FAILED;
        }
    }

    private static final class AndroidBackend implements Backend {
        private final InputMethodService service;
        private final InputMethodManager manager;

        AndroidBackend(InputMethodService service) {
            this.service = service;
            manager = (InputMethodManager) service.getSystemService(
                    InputMethodService.INPUT_METHOD_SERVICE);
        }

        @Override
        public List<InputMethodInfo> enabledMethods() {
            return manager.getEnabledInputMethodList();
        }

        @Override
        public List<InputMethodSubtype> enabledSubtypes(InputMethodInfo method) {
            return manager.getEnabledInputMethodSubtypeList(method, true);
        }

        @Override
        public void switchTo(String id, InputMethodSubtype subtype) {
            if (Build.VERSION.SDK_INT >= 28) {
                service.switchInputMethod(id, subtype);
            } else {
                // Public compatibility API for API 24–27 only; never drop the voice subtype.
                IBinder token = service.getWindow().getWindow().getAttributes().token;
                if (token == null) {
                    throw new IllegalArgumentException("No active IME token");
                }
                manager.setInputMethodAndSubtype(token, id, subtype);
            }
        }
    }
}
