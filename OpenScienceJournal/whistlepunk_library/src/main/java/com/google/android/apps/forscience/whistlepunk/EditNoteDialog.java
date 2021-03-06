/*
 *  Copyright 2016 Google Inc. All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.google.android.apps.forscience.whistlepunk;

import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.google.android.apps.forscience.javalib.MaybeConsumer;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabelValue;
import com.google.android.apps.forscience.whistlepunk.metadata.Label;
import com.google.android.apps.forscience.whistlepunk.metadata.PictureLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.TextLabel;
import com.google.protobuf.nano.InvalidProtocolBufferNanoException;

/**
 * For editing existing notes.
 */
public class EditNoteDialog extends DialogFragment {
    public static final String TAG = "edit_note_dialog";
    private static final String KEY_SAVED_LABEL = "keySavedLabel";
    private static final String KEY_SAVED_TIME_TEXT = "keySavedTimeText";
    private static final String KEY_SAVED_TIMESTAMP = "keySavedTimestamp";
    private static final String KEY_SELECTED_VALUE = "keySavedValue";
    private static final java.lang.String KEY_SAVED_TIME_TEXT_DESCRIPTION =
            "keySavedTimeTextDescription";

    private Label mLabel;
    private GoosciLabelValue.LabelValue mSelectedValue;
    private long mTimestamp;

    public interface EditNoteDialogListener {
        /**
         * Called when a label is being edited in the database. Return value is passed to data
         * controller during the label edit.
         * @return A MaybeConsumer on whether the label was successfully edited..
         */
        public MaybeConsumer<Label> onLabelEdit(Label updatedLabel);

        /**
         * Called when a timestamp was clicked. The responder may use this to allow
         * editing of the label's timestamp, or may ignore it.
         */
        public void onEditNoteTimestampClicked(Label label,
                GoosciLabelValue.LabelValue selectedValue, long selectedTimestamp);
    }

    public static EditNoteDialog newInstance(Label label,
            GoosciLabelValue.LabelValue selectedValue, String timeText, long timestamp,
            String timeTextDescription) {
        EditNoteDialog dialog = new EditNoteDialog();
        Bundle args = new Bundle();
        args.putParcelable(KEY_SAVED_LABEL, label);
        args.putByteArray(KEY_SELECTED_VALUE, ProtoUtils.makeBlob(selectedValue));
        args.putString(KEY_SAVED_TIME_TEXT, timeText);
        args.putLong(KEY_SAVED_TIMESTAMP, timestamp);
        args.putString(KEY_SAVED_TIME_TEXT_DESCRIPTION, timeTextDescription);
        dialog.setArguments(args);
        return dialog;
    }

    public EditNoteDialog() {
    }

    @Override
    public AlertDialog onCreateDialog(Bundle savedInstanceState) {
        mLabel = getArguments().getParcelable(KEY_SAVED_LABEL);
        String timeText = getArguments().getString(KEY_SAVED_TIME_TEXT, "");
        String timeTextContentDescription = getArguments().getString(
                KEY_SAVED_TIME_TEXT_DESCRIPTION);
        mTimestamp = getArguments().getLong(KEY_SAVED_TIMESTAMP);
        try {
            mSelectedValue = GoosciLabelValue.LabelValue.parseFrom(
                    getArguments().getByteArray(KEY_SELECTED_VALUE));
        } catch (InvalidProtocolBufferNanoException ex) {
            Log.wtf(TAG, "Couldn't parse label value");
        }
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(getActivity());

        LinearLayout rootView = (LinearLayout) LayoutInflater.from(getActivity()).inflate(
                R.layout.run_review_label_edit, null);
        alertDialog.setView(rootView);

        ImageView imageView = (ImageView) rootView.findViewById(R.id.picture_note_preview_image);
        final EditText editText = (EditText) rootView.findViewById(R.id.edit_note_text);

        // Use mSelectedValue to load content, because the user may have changed the value since
        // it was stored in the label. Note that picture labels can't be edited at this time,
        // but in the future this will apply to picture labels as well.
        if (mLabel instanceof PictureLabel) {
            imageView.setVisibility(View.VISIBLE);
            editText.setText(PictureLabel.getCaption(mSelectedValue));
            editText.setHint(R.string.picture_note_caption_hint);
            Glide.with(getActivity())
                    .load(PictureLabel.getFilePath(mSelectedValue))
                    .into(imageView);
            imageView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    PictureUtils.launchExternalViewer(getActivity(),
                            (PictureLabel.getFilePath(mSelectedValue)));
                }
            });
        } else if (mLabel instanceof TextLabel) {
            imageView.setVisibility(View.GONE);
            editText.setText(TextLabel.getText(mSelectedValue));
        }

        alertDialog.setPositiveButton(R.string.action_save,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mLabel.setTimestamp(mTimestamp);
                        if (mLabel instanceof  TextLabel) {
                            ((TextLabel) mLabel).setText(editText.getText().toString());
                        } else if (mLabel instanceof PictureLabel) {
                            ((PictureLabel) mLabel).setCaption(editText.getText().toString());
                        }
                        getDataController().editLabel(mLabel,
                                ((EditNoteDialogListener) getParentFragment()).onLabelEdit(
                                        mLabel));
                    }
                }
        );
        alertDialog.setNegativeButton(android.R.string.cancel,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
        alertDialog.setCancelable(true);

        TextView timeTextView = (TextView) rootView.findViewById(R.id.edit_note_time);
        timeTextView.setText(timeText);
        timeTextView.setContentDescription(timeTextContentDescription);
        timeTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                GoosciLabelValue.LabelValue value = new GoosciLabelValue.LabelValue();
                if (mLabel instanceof PictureLabel) {
                    // Captions can be edited, but the picture path cannot be edited at this time.
                    PictureLabel.populateStorageValue(value, ((PictureLabel) mLabel).getFilePath(),
                            editText.getText().toString());
                    ((EditNoteDialogListener) getParentFragment()).onEditNoteTimestampClicked(
                            mLabel, value, mTimestamp);
                } else if (mLabel instanceof TextLabel) {
                    TextLabel.populateStorageValue(value, editText.getText().toString());
                    ((EditNoteDialogListener) getParentFragment()).onEditNoteTimestampClicked(
                            mLabel, value, mTimestamp);
                }
            }
        });

        AlertDialog dialog = alertDialog.create();
        if (mLabel instanceof TextLabel) {
            dialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        return dialog;
    }

    private DataController getDataController() {
        return AppSingleton.getInstance(getActivity()).getDataController();
    }

}
