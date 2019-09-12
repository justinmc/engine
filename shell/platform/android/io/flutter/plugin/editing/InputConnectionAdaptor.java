// Copyright 2013 The Flutter Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugin.editing;

import android.content.Context;
import android.os.Bundle;
import android.text.DynamicLayout;
import android.text.Editable;
import android.text.Layout;
import android.text.Layout.Directions;
import android.text.Selection;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import android.view.inputmethod.InputContentInfo;
import android.view.inputmethod.CorrectionInfo;
import android.view.inputmethod.CompletionInfo;

import io.flutter.embedding.engine.systemchannels.TextInputChannel;
import io.flutter.Log;
import io.flutter.plugin.common.ErrorLogResult;
import io.flutter.plugin.common.MethodChannel;

class InputConnectionAdaptor extends BaseInputConnection {
    private final View mFlutterView;
    private final int mClient;
    private final TextInputChannel textInputChannel;
    private final Editable mEditable;
    private int mBatchCount;
    private InputMethodManager mImm;
    private final Layout mLayout;

    @SuppressWarnings("deprecation")
    public InputConnectionAdaptor(
        View view,
        int client,
        TextInputChannel textInputChannel,
        Editable editable
    ) {
        super(view, true);
        mFlutterView = view;
        mClient = client;
        this.textInputChannel = textInputChannel;
        mEditable = editable;
        mBatchCount = 0;
        // We create a dummy Layout with max width so that the selection
        // shifting acts as if all text were in one line.
        mLayout = new DynamicLayout(mEditable, new TextPaint(), Integer.MAX_VALUE, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        mImm = (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
    }

    private static final String TAG = "InputConnectionAdapter";

    private int lastComposingStart;
    private int lastComposingEnd;
    private int lastExistingComposingStart;
    private int lastExistingComposingEnd;
    private CharSequence lastComposingText;
    private boolean didCommit = false;

    // Send the current state of the editable to Flutter.
    private void updateEditingState() {
        // If the IME is in the middle of a batch edit, then wait until it completes.
        if (mBatchCount > 0)
            return;

        int selectionStart = Selection.getSelectionStart(mEditable);
        int selectionEnd = Selection.getSelectionEnd(mEditable);
        int composingStart = BaseInputConnection.getComposingSpanStart(mEditable);
        int composingEnd = BaseInputConnection.getComposingSpanEnd(mEditable);

        lastComposingStart = composingStart;
        lastComposingEnd = composingEnd;
        if (composingStart >= 0 && composingEnd >= 0) {
          lastExistingComposingStart = composingStart;
          lastExistingComposingEnd = composingEnd;
        }

        mImm.updateSelection(mFlutterView,
                             selectionStart, selectionEnd,
                             composingStart, composingEnd);

        textInputChannel.updateEditingState(
            mClient,
            mEditable.toString(),
            selectionStart,
            selectionEnd,
            composingStart,
            composingEnd
        );

        // TODO(justinmc): Moving the cursor calls here, could call commitText when that happens?
        // But seems like I don't have the necessary parameters to be able to
        // call commitText.
        // What about calling finishComposingText?
        Log.d("justin", "updateEditingState selection,composing: " + selectionStart + " - " + selectionEnd + ", " + composingStart + " - " + composingEnd);
        //super.commitText(mEditable.toString(), selectionStart);
    }

    @Override
    public boolean clearMetaKeyStates(int status) {
      Log.d("justin", "clearMetaKeyStates");
      return super.clearMetaKeyStates(status);
    }

    @Override
    public void closeConnection() {
      Log.d("justin", "closeConnection");
      super.closeConnection();
    }

    @Override
    public boolean commitCompletion(CompletionInfo info) {
      Log.d("justin", "commitcompletion");
      return super.commitCompletion(info);
    }

    @Override
    public boolean commitContent(InputContentInfo info, int flags, Bundle opts) {
      Log.d("justin", "commitContent " + flags);
      return super.commitContent(info, flags, opts);
    }

    @Override
    public boolean commitCorrection(CorrectionInfo correctionInfo) {
      Log.d("justin", "commitCorrection");
      return super.commitCorrection(correctionInfo);
    }

    @Override
    public boolean deleteSurroundingTextInCodePoints(int beforeLength, int afterLength) {
        Log.d("justin", "deleteSurroundingTextInCodePoints " + beforeLength + ", " + afterLength);
        return super.deleteSurroundingTextInCodePoints(beforeLength, afterLength);
    }

    @Override
    public boolean finishComposingText() {
        Log.d("justin", "finishComposingText");
        /*
        if (lastComposingStart == -1 && lastComposingEnd == -1 && !didCommit) {
          didCommit = true;
          final Editable content = getEditable();
          int cursorPosition = Selection.getSelectionEnd(content);
          Log.d("justin", "hack it " + lastComposingText + " | " + cursorPosition);
          commitText(lastComposingText, cursorPosition);
        }
        */
        return super.finishComposingText();
    }

    @Override
    public boolean performContextMenuAction (int id) {
        Log.d("justin", "performContextMenuAction: " + id);
        return super.performContextMenuAction(id);
    }

    @Override
    public boolean performPrivateCommand (String action, Bundle data) {
        Log.d("justin", "performPrivateCommand: " + action);
        return super.performPrivateCommand(action, data);
    }

    @Override
    public boolean requestCursorUpdates (int cursorUpdateMode) {
        Log.d("justin", "requestCursorUpdates " + cursorUpdateMode);
        return super.requestCursorUpdates(cursorUpdateMode);
    }

    @Override
    public Editable getEditable() {
        return mEditable;
    }

    @Override
    public boolean beginBatchEdit() {
        mBatchCount++;
        Log.d("justin", "beginBatchEdit " + mBatchCount);
        return super.beginBatchEdit();
    }

    @Override
    public boolean endBatchEdit() {
        boolean result = super.endBatchEdit();
        mBatchCount--;
        Log.d("justin", "endBatchEdit: " + mBatchCount);
        updateEditingState();
        return result;
    }

    @Override
    public boolean commitText(CharSequence text, int newCursorPosition) {
        Log.d("justin", newCursorPosition + ") commit: " + text);

        // TODO(justinmc): HACK: If composing region is -1,-1, then prevent
        // committing more than one character.
        if (lastComposingStart == -1 && lastComposingEnd == -1) {
          text = text.subSequence(0, 1);
        }

        boolean result = super.commitText(text, newCursorPosition);
        updateEditingState();
        return result;
    }

    @Override
    public boolean deleteSurroundingText(int beforeLength, int afterLength) {
        Log.d("justin", "deleteSurroundingText");
        if (Selection.getSelectionStart(mEditable) == -1)
            return true;

        boolean result = super.deleteSurroundingText(beforeLength, afterLength);
        updateEditingState();
        return result;
    }

    // TODO(justinmc):
    //
    // Good device:
    // (composing region)
    // (0,1) L|
    // (0,2) LO|
    // (0,3) LOL|
    // (0,4) LOLO|
    // (-1,-1) LO|LO
    // setComposingText L
    // (2,3) LOL|LO
    //
    // Bad device:
    // (0,1) L|
    // (0,2) LO|
    // (0,3) LOL|
    // (0,4) LOLO|
    // (-1,-1) LO|LO
    // setComposingRegion (0,2)
    // setComposingText LOLOL
    // (0,5) LOLOL|LO
    //
    // About the call to setComposingRegion on the bad device.
    // The effect is that it replaces the composing region (0,2 LO) with the new
    // composing text when setComposingText is called. If I comment it out, then
    // it just inserts the text from setComposingText without erasing anything,
    // so the result is (2,7) LOLOLOL|LO.
    //
    // So right now my understanding of the problem is this:
    //   1. setComposingRegion is called before inserting a new character when
    //      it shouldn't be, and it's called with strange values that I don't
    //      fully understand.
    //   2. When inserting that new character, setComposingText is called with
    //      the entire new text (or something more complicated?) when it should
    //      be called with just the new single character.
    //   3. Sometimes commit is called instead of setComposingText, and this
    //      produces a similar bug where characters are not inserted at the
    //      cursor. I'm not sure exactly how to reproduce.
    //
    // As a potential solution, I tried calling commitText when the cursor is
    // moved. This does commit the composing text, but it doesn't affect the
    // incorrect calls that come back to setComposingRegion and setComposingText!
    // They still happen identically to before.
    //
    // Here is the difference between the unaffected and affected keyboards
    // natively:
    //
    //   1. Type a consonant.
    //   2. Type a space.
    //   3. Type backspace.
    //   4. Type a vowel.
    // The new affected keyboard sets the composing region to both characters
    // for convenience.  The old unaffected keyboard just sets the composing
    // region to the vowel, which is annoying.
    //
    // I'm continuing to try to hack it, but I really want to find an elegant
    // solution. What if I spin up a native Android app and log every method in
    // this file for reference? I want to know what needs to be called when I
    // change cursor position.
    @Override
    public boolean setComposingRegion(int start, int end) {
        Log.d("justin", "setComposingRegion: " + start + ", " + end);

        // TODO(justinmc): HACK: Can't change composing region after it was
        // empty.
        if (lastComposingStart == -1 && lastComposingEnd == -1) {
          return false;
        }
        /*
        if (end - start > lastComposingEnd - lastComposingStart + 1) {
          start = lastComposingStart;
          end = lastComposingStart;
        }
        */

        /*
        // TODO(justinmc): Hack #1: when this is called and currently -1,-1, set it
        // instead to most recent non-empty region.
        // Works for the main bug case, but there are others that still fail.
        if (lastComposingStart == -1 && lastComposingEnd == -1) {
          start = lastExistingComposingStart;
          end = lastExistingComposingEnd;
          Log.d("justin", "setComposingRegion actually jk: " + start + ", " + end);
        }
        */

        /*
        if (lastComposingStart == -1 && lastComposingEnd == -1) {
          commitText();
          updateEditingState();
        }
        */

        boolean result = super.setComposingRegion(start, end);
        updateEditingState();
        return result;
    }

    @Override
    public boolean setComposingText(CharSequence text, int newCursorPosition) {
        Log.d("justin", newCursorPosition + ": setComposingText " + text);
        boolean result;
        if (text.length() == 0) {
            result = super.commitText(text, newCursorPosition);
        } else {
            if (text != null) {
              // TODO(justinmc): HACK: When the length of the text here is more
              // than 1 character longer than the last composingLength, then
              // there was a broken jump of some sort. Only accept one extra
              // character and ignore the rest.
              int composingLength = lastComposingEnd - lastComposingStart;
              if (text.length() > composingLength + 1) {
              //if (lastComposingStart == -1 && lastComposingEnd == -1 && text.length() > 1) {
                //text = text.subSequence(text.length() - composingLength - 1, text.length() - composingLength);
                text = text.subSequence(text.length() - composingLength - 1, text.length());
              }
            }
            lastComposingText = text;
            didCommit = true;

            result = super.setComposingText(text, newCursorPosition);
        }
        updateEditingState();
        return result;
    }

    @Override
    public boolean setSelection(int start, int end) {
        Log.d("justin", "setSelection: " + start + ", " + end);
        boolean result = super.setSelection(start, end);
        updateEditingState();
        return result;
    }

    // Sanitizes the index to ensure the index is within the range of the
    // contents of editable.
    private static int clampIndexToEditable(int index, Editable editable) {
        int clamped = Math.max(0, Math.min(editable.length(), index));
        if (clamped != index) {
            Log.d("flutter", "Text selection index was clamped ("
                + index + "->" + clamped
                + ") to remain in bounds. This may not be your fault, as some keyboards may select outside of bounds."
            );
        }
        return clamped;
    }

    @Override
    public boolean sendKeyEvent(KeyEvent event) {
        Log.d("justin", "sendKeyEvent");
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_DEL) {
                int selStart = clampIndexToEditable(Selection.getSelectionStart(mEditable), mEditable);
                int selEnd = clampIndexToEditable(Selection.getSelectionEnd(mEditable), mEditable);
                if (selEnd > selStart) {
                    // Delete the selection.
                    Selection.setSelection(mEditable, selStart);
                    mEditable.delete(selStart, selEnd);
                    updateEditingState();
                    return true;
                } else if (selStart > 0) {
                    // Delete to the left/right of the cursor depending on direction of text.
                    // TODO(garyq): Explore how to obtain per-character direction. The
                    // isRTLCharAt() call below is returning blanket direction assumption
                    // based on the first character in the line.
                    boolean isRtl = mLayout.isRtlCharAt(mLayout.getLineForOffset(selStart));
                    try {
                        if (isRtl) {
                            Selection.extendRight(mEditable, mLayout);
                        } else {
                            Selection.extendLeft(mEditable, mLayout);
                        }
                    } catch (IndexOutOfBoundsException e) {
                        // On some Chinese devices (primarily Huawei, some Xiaomi),
                        // on initial app startup before focus is lost, the
                        // Selection.extendLeft and extendRight calls always extend
                        // from the index of the initial contents of mEditable. This
                        // try-catch will prevent crashing on Huawei devices by falling
                        // back to a simple way of deletion, although this a hack and
                        // will not handle emojis.
                        Selection.setSelection(mEditable, selStart, selStart - 1);
                    }
                    int newStart = clampIndexToEditable(Selection.getSelectionStart(mEditable), mEditable);
                    int newEnd = clampIndexToEditable(Selection.getSelectionEnd(mEditable), mEditable);
                    Selection.setSelection(mEditable, Math.min(newStart, newEnd));
                    // Min/Max the values since RTL selections will start at a higher
                    // index than they end at.
                    mEditable.delete(Math.min(newStart, newEnd), Math.max(newStart, newEnd));
                    updateEditingState();
                    return true;
                }
            } else if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT) {
                int selStart = Selection.getSelectionStart(mEditable);
                int newSel = Math.max(selStart - 1, 0);
                setSelection(newSel, newSel);
                return true;
            } else if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT) {
                int selStart = Selection.getSelectionStart(mEditable);
                int newSel = Math.min(selStart + 1, mEditable.length());
                setSelection(newSel, newSel);
                return true;
            } else {
                // Enter a character.
                int character = event.getUnicodeChar();
                if (character != 0) {
                    int selStart = Math.max(0, Selection.getSelectionStart(mEditable));
                    int selEnd = Math.max(0, Selection.getSelectionEnd(mEditable));
                    if (selEnd != selStart)
                        mEditable.delete(selStart, selEnd);
                    mEditable.insert(selStart, String.valueOf((char) character));
                    setSelection(selStart + 1, selStart + 1);
                    updateEditingState();
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean performEditorAction(int actionCode) {
        Log.d("justin", "performEditorAction: " + actionCode);
        switch (actionCode) {
            case EditorInfo.IME_ACTION_NONE:
                textInputChannel.newline(mClient);
                break;
            case EditorInfo.IME_ACTION_UNSPECIFIED:
                textInputChannel.unspecifiedAction(mClient);
                break;
            case EditorInfo.IME_ACTION_GO:
                textInputChannel.go(mClient);
                break;
            case EditorInfo.IME_ACTION_SEARCH:
                textInputChannel.search(mClient);
                break;
            case EditorInfo.IME_ACTION_SEND:
                textInputChannel.send(mClient);
                break;
            case EditorInfo.IME_ACTION_NEXT:
                textInputChannel.next(mClient);
                break;
            case EditorInfo.IME_ACTION_PREVIOUS:
                textInputChannel.previous(mClient);
                break;
            default:
            case EditorInfo.IME_ACTION_DONE:
                textInputChannel.done(mClient);
                break;
        }
        return true;
    }
}
