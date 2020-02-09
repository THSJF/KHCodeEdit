/*
 * Copyright (C) 2018 Light Team Software
 *
 * This file is part of ModPE IDE.
 *
 * ModPE IDE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ModPE IDE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.github.meng.editor.processor;

import android.content.*;
import android.content.res.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.text.*;
import android.text.style.*;
import android.util.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import com.github.meng.editor.document.commons.*;
import com.github.meng.editor.document.suggestions.*;
import com.github.meng.editor.interfaces.*;
import com.github.meng.editor.manager.*;
import com.github.meng.editor.processor.style.*;
import com.github.meng.editor.processor.utils.*;
import com.github.meng.editor.processor.utils.text.*;
import com.github.meng.editor.widget.*;
import com.github.meng.sample.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.regex.*;

import android.content.ClipboardManager;
import com.github.meng.editor.processor.style.StyleSpan;

public class TextProcessor extends MultiAutoCompleteTextView implements View.OnKeyListener {

    private static final String TAG = TextProcessor.class.getSimpleName();

    private static final String TAB_STR = "    "; //4 spaces
    boolean mBracketMatching = true;
    boolean mInsertBracket = true;
    int mOnTextChangedChangeEnd;
    int mOnTextChangedChangeStart;
    String mOnTextChangedNewText;
    int mAddedTextCount;
    String mNewText;
    String mOldText;
    float mPreviousTouchX = 0.0f;
    float mPreviousTouchY = 0.0f;
    int mMaximumVelocity;
    int mGutterWidth;
    int mLineNumberDigitCount = 0;
    int mCharHeight = 0;
    int mIdealMargin;
    int mTopDirtyLine = 0;
    int mBottomDirtyLine = 0;
    boolean zoomPinch = false;
    float zoomPinchFactor;
    float textSize;
    int h;
    private ITextProcessorSetting defaultSetting;
    private CodeEditor codeEditor;
    private ClipboardManager mClipboardManager;
    private Context mContext;
    private Scroller mScroller;
    private OnScrollChangedListener[] mScrollChangedListeners;
    private VelocityTracker mVelocityTracker;
    private LineUtils mLineUtils;
    private TypedValue mColorSearchSpan;
    private TextChange mUpdateLastChange;
    private boolean isDoingUndoRedo = false;
    private boolean isAutoIndenting = false;
    private UndoStack mRedoStack;
    private UndoStack mUndoStack;
    private StylePaint mLineNumberPaint;
    private StylePaint mGutterBackgroundPaint;
    private StylePaint mLinePaint;
    private StylePaint mSelectedLinePaint;
    private StyleSpan mSyntaxNumbers;
    private StyleSpan mSyntaxSymbols;
    private StyleSpan mSyntaxBrackets;
    private StyleSpan mSyntaxKeywords;
    private StyleSpan mSyntaxMethods;
    private StyleSpan mSyntaxStrings;
    private StyleSpan mSyntaxComments;
    private BackgroundColorSpan mOpenBracketSpan;

    private BackgroundColorSpan mClosedBracketSpan;

    public TextProcessor(Context context) {
        super(context);
        mContext = context;
    }

    public TextProcessor(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    public TextProcessor(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mContext = context;
    }

    protected static void clearSpans(Editable e, boolean foregroundSpans, boolean backgroundSpans, boolean syntaxSpans) {
        if (foregroundSpans) {
            ForegroundColorSpan spans[] = e.getSpans(0, e.length(), ForegroundColorSpan.class);
            for (ForegroundColorSpan span : spans) {
                e.removeSpan(span);
            }
        }
        if (backgroundSpans) {
            BackgroundColorSpan spans[] = e.getSpans(0, e.length(), BackgroundColorSpan.class);
            for (BackgroundColorSpan span : spans) {
                e.removeSpan(span);
            }
        }
        if (syntaxSpans) {
            SyntaxHighlightSpan[] spans = e.getSpans(0, e.length(), SyntaxHighlightSpan.class);
            for (SyntaxHighlightSpan span : spans) {
                e.removeSpan(span);
            }
        }
    }

    public void init(CodeEditor codeEditor) {
        this.codeEditor = codeEditor;
        if (!isInEditMode()) {
            initParameters();
            initTheme();
            initMethods();
            postInit();
        }
    }

    protected void initParameters() {
        defaultSetting = new DefaultSetting(mContext);
        mClipboardManager = (ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
        mScroller = new Scroller(mContext);
        mScrollChangedListeners = new OnScrollChangedListener[0];
        mLineUtils = new LineUtils();
    }

    protected void initTheme() {
        TypedValue colorAttr;
        Resources.Theme theme = mContext.getTheme();
        mLineNumberPaint = new StylePaint(true, false);
        colorAttr = new TypedValue();
        int color = getContext().getResources().getColor(R.color.colorNumbersText);
        if (!theme.resolveAttribute(R.attr.colorNumbersText, colorAttr, true)) {
            theme.resolveAttribute(R.attr.colorNumbersText, colorAttr, true);
            color = colorAttr.data;
            if (color == 0)
                color = getContext().getResources().getColor(R.color.colorNumbersText);
        }
        mLineNumberPaint.setColor(color);
        mLineNumberPaint.setTextAlign(StylePaint.Align.RIGHT);
        mLineNumberPaint.setTextSize(getTextSize());
        mLinePaint = new StylePaint(false, false);
        mLinePaint.setColor(mLineNumberPaint.getColor());
        mLinePaint.setStyle(StylePaint.Style.STROKE);


        mGutterBackgroundPaint = new StylePaint(false, false);
        colorAttr = new TypedValue();

        if (!theme.resolveAttribute(R.attr.colorNumbersBackground, colorAttr, true)) {
            theme.resolveAttribute(R.attr.colorNumbersBackground, colorAttr, true);
            color = colorAttr.data;
            if (color == 0)
                color = getContext().getResources().getColor(R.color.colorNumbersBackground);
        }
        mGutterBackgroundPaint.setColor(color);

        mSelectedLinePaint = new StylePaint(false, false);

        if (!theme.resolveAttribute(R.attr.colorSelectedLine, colorAttr, true)) {
            theme.resolveAttribute(R.attr.colorSelectedLine, colorAttr, true);
            color = colorAttr.data;
            if (color == 0)
                color = getContext().getResources().getColor(R.color.colorSelectedLine);
        }
        mSelectedLinePaint.setColor(color);

        //endregion Paints

        mColorSearchSpan = new TypedValue();

        if (!theme.resolveAttribute(R.attr.syntaxNumbers, colorAttr, true)) {
            theme.resolveAttribute(R.attr.syntaxNumbers, colorAttr, true);
            color = colorAttr.data;
            if (color == 0)
                color = getContext().getResources().getColor(R.color.syntaxNumbers);
        }
        mSyntaxNumbers = new StyleSpan(color, false, false);

        colorAttr = new TypedValue();

        if (!theme.resolveAttribute(R.attr.syntaxSymbols, colorAttr, true)) {
            theme.resolveAttribute(R.attr.syntaxSymbols, colorAttr, true);
            color = colorAttr.data;
            if (color == 0)
                color = getContext().getResources().getColor(R.color.syntaxSymbols);
        }

        mSyntaxSymbols = new StyleSpan(color, false, false);

        colorAttr = new TypedValue();

        if (!theme.resolveAttribute(R.attr.syntaxBrackets, colorAttr, true)) {
            theme.resolveAttribute(R.attr.syntaxBrackets, colorAttr, true);
            color = colorAttr.data;
            if (color == 0)
                color = getContext().getResources().getColor(R.color.syntaxBrackets);
        }
        mSyntaxBrackets = new StyleSpan(color, false, false);

        colorAttr = new TypedValue();

        if (!theme.resolveAttribute(R.attr.syntaxKeywords, colorAttr, true)) {
            theme.resolveAttribute(R.attr.syntaxKeywords, colorAttr, true);
            color = colorAttr.data;
            if (color == 0)
                color = getContext().getResources().getColor(R.color.syntaxKeywords);
        }
        mSyntaxKeywords = new StyleSpan(color, false, false);

        colorAttr = new TypedValue();

        if (!theme.resolveAttribute(R.attr.syntaxMethods, colorAttr, true)) {
            theme.resolveAttribute(R.attr.syntaxMethods, colorAttr, true);
            color = colorAttr.data;
            if (color == 0)
                color = getContext().getResources().getColor(R.color.syntaxMethods);
        }

        mSyntaxMethods = new StyleSpan(color, false, false);

        colorAttr = new TypedValue();

        if (!theme.resolveAttribute(R.attr.syntaxStrings, colorAttr, true)) {
            theme.resolveAttribute(R.attr.syntaxStrings, colorAttr, true);
            color = colorAttr.data;
            if (color == 0)
                color = getContext().getResources().getColor(R.color.syntaxStrings);
        }

        mSyntaxStrings = new StyleSpan(color, false, false);

        colorAttr = new TypedValue();

        if (!theme.resolveAttribute(R.attr.syntaxComments, colorAttr, true)) {
            theme.resolveAttribute(R.attr.syntaxComments, colorAttr, true);
            color = colorAttr.data;
            if (color == 0)
                color = getContext().getResources().getColor(R.color.syntaxComments);
        }

        mSyntaxComments = new StyleSpan(color, false, true);

        colorAttr = new TypedValue();

        if (!theme.resolveAttribute(R.attr.colorBracketSpan, colorAttr, true)) {
            theme.resolveAttribute(R.attr.colorBracketSpan, colorAttr, true);
            color = colorAttr.data;
            if (color == 0)
                color = getContext().getResources().getColor(R.color.colorBracketSpan);
        }

        mOpenBracketSpan = new BackgroundColorSpan(color);
        mClosedBracketSpan = new BackgroundColorSpan(color);

        colorAttr = new TypedValue();

        if (!theme.resolveAttribute(R.attr.colorCursor, colorAttr, true)) {
            theme.resolveAttribute(R.attr.colorCursor, colorAttr, true);
            color = colorAttr.data;
            if (color == 0)
                color = getContext().getResources().getColor(R.color.colorCursor);
        }

        setCursorColor(color); //Cursor Color

        colorAttr = new TypedValue();
        color = getContext().getResources().getColor(R.color.colorSelection);
        if (!theme.resolveAttribute(R.attr.colorSelection, colorAttr, true)) {
            theme.resolveAttribute(R.attr.colorSelection, colorAttr, true);
            color = colorAttr.data;
            if (color == 0)
                color = getContext().getResources().getColor(R.color.colorNumbersText);
        }
        setHighlightColor(color); //Selection Color
    }

    protected void initMethods() {
        ViewConfiguration configuration = ViewConfiguration.get(mContext);
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity() * 100;
        mIdealMargin = Converter.dpAsPixels(this, 4);
        setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        setOnKeyListener(this);
    }

    //endregion INIT

    //region BASE_METHODS

    protected void postInit() {
        postInvalidate();
        refreshDrawableState();
        invalidateCharHeight();
    }

    protected void invalidateCharHeight() {
        mCharHeight = (int) Math.ceil(getPaint().getFontSpacing());
        mCharHeight = (int) getPaint().measureText("M");
    }

    @Override
    public void setTextSize(float textSize) {
        super.setTextSize(textSize);
        if (mLineNumberPaint != null) {
            mLineNumberPaint.setTextSize(getTextSize());
        }
    }

    @Override
    public Parcelable onSaveInstanceState() {
        try {
            return super.onSaveInstanceState();
        } catch (Exception e) {
            Logger.error(TAG, e);
            return BaseSavedState.EMPTY_STATE;
        }
    }

    @Override
    public void onSizeChanged(int w, int h, int oldw, int oldh) {
        syntaxHighlight(getLayout(), getEditableText(), getLineHeight(), getLineCount(), getScrollY(), getHeight());
        //Scroller
        for (OnScrollChangedListener l : mScrollChangedListeners) {
            int x = getScrollX();
            int y = getScrollY();
            l.onScrollChanged(x, y, x, y);
        }
            onDropDownChangeSize(w, h);
    }

    private void updateDocumentBeforeTextChanged(int start, int count) {
        mOnTextChangedChangeStart = start;
        mOnTextChangedChangeEnd = start + count;
    }

    private void updateDocumentOnTextChanged(CharSequence s, int start, int count) {
        mOnTextChangedNewText = s.subSequence(start, start + count).toString();
        codeEditor.replaceText(mOnTextChangedChangeStart, mOnTextChangedChangeEnd, mOnTextChangedNewText);
    }

    private void updateUndoRedoOnTextChanged(CharSequence s, int start, int count) {
        if (!isDoingUndoRedo && mUpdateLastChange != null) {
            if (count < UndoStack.MAX_SIZE) {
                mUpdateLastChange.newText = s.subSequence(start, start + count).toString();
                if (start == mUpdateLastChange.start &&
                        ((mUpdateLastChange.oldText.length() > 0
                                || mUpdateLastChange.newText.length() > 0)
                                && !mUpdateLastChange.oldText.equals(mUpdateLastChange.newText))) {
                    mUndoStack.push(mUpdateLastChange);
                    mRedoStack.removeAll();
                }
            } else {
                mUndoStack.removeAll();
                mRedoStack.removeAll();
            }
            mUpdateLastChange = null;
        }
    }

    private void updateUndoRedoBeforeTextChanged(CharSequence s, int start, int count) {
        if (!isDoingUndoRedo) {
            if (count < UndoStack.MAX_SIZE) {
                mUpdateLastChange = new TextChange();
                mUpdateLastChange.oldText = s.subSequence(start, start + count).toString();
                mUpdateLastChange.start = start;
                return;
            }
            mUndoStack.removeAll();
            mRedoStack.removeAll();
            mUpdateLastChange = null;
        }
    }

    @Override
    public void onSelectionChanged(int selStart, int selEnd) {
        if (selStart == selEnd)
            checkMatchingBracket(selStart);
        invalidate();
    }

    protected Editable getSelectedText() {
        if (getSelectionEnd() > getSelectionStart()) {
            return (Editable) getText().subSequence(getSelectionStart(), getSelectionEnd());
        }
        return (Editable) getText().subSequence(getSelectionEnd(), getSelectionStart());
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.isCtrlPressed()) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_X: // CTRL+X - Cut
                    cut();
                    return true;
                case KeyEvent.KEYCODE_C: // CTRL+C - Copy
                    copy();
                    return true;
                case KeyEvent.KEYCODE_V: // CTRL+V - Paste
                    paste();
                    return true;
                case KeyEvent.KEYCODE_Z: // CTRL+Z - Undo
                    undo();
                    return true;
                case KeyEvent.KEYCODE_Y: // CTRL+Y - Redo
                    redo();
                    return true;
                case KeyEvent.KEYCODE_A: // CTRL+A - Select All
                    selectAll();
                    return true;
                case KeyEvent.KEYCODE_DEL: // CTRL+Delete - Delete Line
                    deleteLine();
                    return true;
                case KeyEvent.KEYCODE_D: // CTRL+D - Duplicate Line
                    duplicateLine();
                    return true;
//                case KeyEvent.KEYCODE_S: // CTRL+S - Save File
//                    codeEditor.saveFile();
//                    return true;
                default:
                    return super.onKeyDown(keyCode, event);
            }
        } else {
            switch (keyCode) {
                case KeyEvent.KEYCODE_TAB: // TAB
                    int start, end;
                    start = Math.max(getSelectionStart(), 0);
                    end = Math.max(getSelectionEnd(), 0);
                    getText().replace(Math.min(start, end),
                            Math.max(start, end), TAB_STR, 0, TAB_STR.length());
                    return true;
                default:
                    try {
                        return super.onKeyDown(keyCode, event);
                    } catch (Exception e) {
                        Logger.error(TAG, e);
                    }
                    return false;
            }
        }
    }

    //endregion BASE_METHODS

    //region INDENTATION

    @Override
    public boolean onKey(View view, int keyCode, KeyEvent event) {
        return false;
    }

    private void generalOnTextChanged(int start, int count) {
        if (!isDoingUndoRedo && !isAutoIndenting) {
            final String replacementValue;
            final int newCursorPosition;
            String[] result = executeIndentation(start);
            if (result[0] != null || result[1] != null) {
                String preText = result[0] != null ? result[0] : "";
                String postText = result[1] != null ? result[1] : "";
                if (!preText.equals("") || !postText.equals("")) {
                    replacementValue = String.valueOf(preText) + mNewText + postText;
                } else {
                    return;
                }
            } else if (result[2] != null) {
                replacementValue = result[2];
            } else {
                return;
            }
            if (result[3] != null) {
                newCursorPosition = Integer.parseInt(result[3]);
            } else {
                newCursorPosition = start + replacementValue.length();
            }
            final int i = start;
            final int i2 = count;
            post(new Runnable(){

					@Override
					public void run() {
						isAutoIndenting = true;
						getText().replace(i, i + i2, replacementValue);
						mUndoStack.pop();
						TextChange change = mUndoStack.pop();
						if (!replacementValue.equals("")) {
							change.newText = replacementValue;
							mUndoStack.push(change);
						}
						Selection.setSelection(getText(), newCursorPosition);
						isAutoIndenting = false;
					}
				});   
        }
    }

    private String[] executeIndentation(int start) {
        if (codeEditor != null) {
            String[] strArr;
            if (mNewText.equals("\n")) {
                String prevLineIndentation = getIndentationForOffset(start);
                StringBuilder indentation = new StringBuilder(prevLineIndentation);
                int newCursorPosition = (indentation.length() + start) + 1;
                if (start > 0 && getText().charAt(start - 1) == '{') {
                    indentation.append(TAB_STR);
                    newCursorPosition = (indentation.length() + start) + 1;
                }
                if (start + 1 < getText().length() && getText().charAt(start + 1) == '}') {
                    indentation.append("\n").append(prevLineIndentation);
                }
                strArr = new String[4];
                strArr[1] = indentation.toString();
                strArr[3] = Integer.toString(newCursorPosition);
                return strArr;
            } else if (mInsertBracket && mNewText.equals("{")) {
                strArr = new String[4];
                strArr[1] = "}";
                strArr[3] = Integer.toString(start + 1);
                return strArr;
            } else if (mInsertBracket && mNewText.equals("}")) {
                if (start + 1 < getText().length() && getText().charAt(start + 1) == '}') {
                    strArr = new String[4];
                    strArr[2] = "";
                    strArr[3] = Integer.toString(start + 1);
                    return strArr;
                }
            } else if (mInsertBracket && mNewText.equals("(")) {
                strArr = new String[4];
                strArr[1] = ")";
                strArr[3] = Integer.toString(start + 1);
                return strArr;
            } else if (mInsertBracket && mNewText.equals(")")) {
                if (start + 1 < getText().length() && getText().charAt(start + 1) == ')') {
                    strArr = new String[4];
                    strArr[2] = "";
                    strArr[3] = Integer.toString(start + 1);
                    return strArr;
                }
            } else if (mInsertBracket && mNewText.equals("[")) {
                strArr = new String[4];
                strArr[1] = "]";
                strArr[3] = Integer.toString(start + 1);
                return strArr;
            } else if (mInsertBracket && mNewText.equals("]")
                    && start + 1 < getText().length() && getText().charAt(start + 1) == ']') {
                strArr = new String[4];
                strArr[2] = "";
                strArr[3] = Integer.toString(start + 1);
                return strArr;
            }
        }
        return new String[4];
    }

    public String getIndentationForOffset(int offset) {
        return getIndentationForLine(codeEditor.getLinesCollection().getLineForIndex(offset));
    }

    //endregion INDENTATION

    //region SUGGESTION_METHODS

    public String getIndentationForLine(int line) {
        LineObject l = codeEditor.getLinesCollection().getLine(line);
        if (l == null) {
            return "";
        }
        int start = l.getStart();
        int i = start;
        while (i < getText().length()) {
            char c = getText().charAt(i);
            if (!Character.isWhitespace(c) || c == '\n') {
                break;
            }
            i++;
        }
        return getText().subSequence(start, i).toString();
    }

    @Override
    public void showDropDown() {
        if (!isPopupShowing()) {
            if (hasFocus()) {
                super.showDropDown();
            }
        }
    }

    protected void loadSuggestions() {
        if (codeEditor.getLanguage() != null) {
            ArrayList<SuggestionItem> data = new ArrayList<>();
            for (String name : codeEditor.getLanguage().getAllCompletions()) {
                data.add(new SuggestionItem(SuggestionType.TYPE_KEYWORD, name)); //Keyword
            }
            setSuggestData(data);
        }
    }

    protected void setSuggestData(ArrayList<SuggestionItem> data) {
        SuggestionAdapter mAdapter =
                new SuggestionAdapter(mContext, R.layout.item_list_suggest, data);
        setAdapter(mAdapter);
    }

    protected int getHeightVisible() {
        Rect rect = new Rect();
        getWindowVisibleDisplayFrame(rect);
        return rect.bottom - rect.top;
    }

    protected void onDropDownChangeSize(int w, int h) {
        Rect rect = new Rect();
        getWindowVisibleDisplayFrame(rect);

        Logger.debug(TAG, "onDropdownChangeSize: " + rect);

        // 1/2 width of screen
        setDropDownWidth((int) (w * 0.5f));

        // 0.5 height of screen
        setDropDownHeight((int) (h * 0.5f));
        this.h = h;
        //change position
        onPopupChangePosition();
    }

    protected void onPopupChangePosition() {
        try {
            Layout layout = getLayout();
            if (layout != null) {

                int pos = getSelectionStart();
                int line = layout.getLineForOffset(pos);
                int baseline = layout.getLineBaseline(line);
                int ascent = layout.getLineAscent(line);

                Rect bounds = new Rect();
                Paint textPaint = getPaint();
                String sample="A";
                textPaint.getTextBounds(sample, 0, sample.length(), bounds);
                int width = bounds.width()/sample.length();


                float x = layout.getPrimaryHorizontal(pos);
                float y = baseline + ascent;

                int offsetHorizontal = (int) x + mGutterWidth;
                setDropDownHorizontalOffset(offsetHorizontal);

                int heightVisible = getHeightVisible();
                int offsetVertical = (int) ((y + mCharHeight) - getScrollY());

                int tmp = offsetVertical + getDropDownHeight() + mCharHeight;
                //if (tmp < heightVisible) {
                    tmp = -h + ((offsetVertical*2 / (mCharHeight)) * (mCharHeight / 2))+(mCharHeight/2);
                    setDropDownVerticalOffset(tmp);
                    //((Activity)(mContext)).setTitle("ov :"+offsetVertical +" ch "+mCharHeight+" tmp"+tmp +"h "+h+"p:"+pos);
//                } else {
//                    tmp = offsetVertical - getDropDownHeight() - mCharHeight;
//                    setDropDownVerticalOffset(tmp);
//                    ((Activity)(mContext)).setTitle(" 2 tmp :"+tmp);
//                }


//                int pos = getSelectionStart();
//                int line = layout.getLineForOffset(pos);
//                int baseline = layout.getLineBaseline(line);
//                int ascent = layout.getLineAscent(line);
//
//                float x = layout.getPrimaryHorizontal(pos);
//                float y = baseline + ascent;
//
//                int offsetHorizontal = (int) x + mGutterWidth;
//                setDropDownHorizontalOffset(offsetHorizontal);
//
//                //    int heightVisible = getHeightVisible();
//                int offsetVertical = (int) ((y + mCharHeight) - getScrollY());
//
//                int tmp = offsetVertical + getDropDownHeight() + mCharHeight;
////                if (tmp < heightVisible) {
//                tmp = -(offsetVertical + mCharHeight) + ((offsetVertical / mCharHeight) * (mCharHeight / 2));
//                setDropDownVerticalOffset(tmp);
////                } else {
////                    tmp = offsetVertical - getDropDownHeight() - mCharHeight;
////                    setDropDownVerticalOffset(tmp);
////                }

            }
        } catch (Exception e) {
            Logger.error(TAG, e);
        }
    }

    //endregion SUGGESTION_METHODS

    //region SCROLLER_METHODS

    @Override
    public void onScrollChanged(int horiz, int vert, int oldHoriz, int oldVert) {
        super.onScrollChanged(horiz, vert, oldHoriz, oldVert);
        if (mScrollChangedListeners != null) {
            for (OnScrollChangedListener l : mScrollChangedListeners) {
                l.onScrollChanged(horiz, vert, oldHoriz, oldVert);
            }
        }
        if (mTopDirtyLine > mLineUtils.getTopVisibleLine(this)
                || mBottomDirtyLine < mLineUtils.getBottomVisibleLine(this)) {
            //Очищаем старые syntax-спаны, чтобы наложить новые
            clearSpans(getEditableText(), false, false, true);
            syntaxHighlight(getLayout(), getEditableText(), getLineHeight(),
                    getLineCount(), getScrollY(), getHeight());
        }
    }

    public void addOnScrollChangedListener(OnScrollChangedListener listener) {
        OnScrollChangedListener[] newListener =
                new OnScrollChangedListener[mScrollChangedListeners.length + 1];
        int length = mScrollChangedListeners.length;
        System.arraycopy(mScrollChangedListeners, 0, newListener, 0, length);
        newListener[newListener.length - 1] = listener;
        mScrollChangedListeners = newListener;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (!mScroller.isFinished()) {
                    mScroller.abortAnimation();
                }
                if (mVelocityTracker == null) {
                    mVelocityTracker = VelocityTracker.obtain();
                } else {
                    mVelocityTracker.clear();
                }
                mPreviousTouchX = event.getX();
                mPreviousTouchY = event.getY();
                super.onTouchEvent(event);
                break;
            case MotionEvent.ACTION_UP:
                int velocityX;
                mVelocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
                int velocityY = (int) mVelocityTracker.getYVelocity();
                if (defaultSetting.getWrapContent()) {
                    velocityX = 0;
                } else {
                    velocityX = (int) mVelocityTracker.getXVelocity();
                }
                mPreviousTouchX = 0.0f;
                mPreviousTouchY = 0.0f;
                if (Math.abs(velocityY) >= 0 || Math.abs(velocityX) >= 0) {
                    if (getLayout() == null) {
                        return super.onTouchEvent(event);
                    }
                    mScroller.fling(
                            getScrollX(), getScrollY(),
                            -velocityX, -velocityY,
                            0, ((getLayout().getWidth() - getWidth())
                                    + getPaddingLeft()) + getPaddingRight(),
                            0, ((getLayout().getHeight() - getHeight())
                                    + getPaddingTop()) + getPaddingBottom());
                } else if (mVelocityTracker != null) {
                    mVelocityTracker.recycle();
                    mVelocityTracker = null;
                }
                super.onTouchEvent(event);
                break;
            case MotionEvent.ACTION_MOVE:
                mVelocityTracker.addMovement(event);
                mPreviousTouchX = event.getX();
                mPreviousTouchY = event.getY();
                super.onTouchEvent(event);
                break;
            default:
                super.onTouchEvent(event);
                break;
        }
        return true;
    }

    /**
     * Останавливает анимацию скроллинга.
     */
    public void abortFling() {
        if (mScroller != null && !mScroller.isFinished()) {
            mScroller.abortAnimation();
        }
    }

    @Override
    public void computeScroll() {
        if (!isInEditMode() && mScroller.computeScrollOffset()) {
            scrollTo(mScroller.getCurrX(), mScroller.getCurrY());
        }
    }

    //endregion SCROLLER_METHODS

    //region LINE_NUMBERS_METHODS

    @Override
    public void onDraw(Canvas canvas) {
        if (!isInEditMode()) {
            int top;
            Layout layout = getLayout();
            if (layout != null && codeEditor != null) {
                int currentLineStart = codeEditor.getLineForIndex(getSelectionStart());
                if (currentLineStart == codeEditor.getLineForIndex(getSelectionEnd())) {
                    int selectedLineStartIndex = codeEditor.getIndexForStartOfLine(currentLineStart);
                    int selectedLineEndIndex = codeEditor.getIndexForEndOfLine(currentLineStart);
                    int topVisualLine = layout.getLineForOffset(selectedLineStartIndex);
                    int bottomVisualLine = layout.getLineForOffset(selectedLineEndIndex);
                    int left = mGutterWidth;
                    top = layout.getLineTop(topVisualLine) + getPaddingTop();
                    int right = (layout.getWidth() + getPaddingLeft()) + getPaddingRight();
                    int bottom = layout.getLineBottom(bottomVisualLine) + getPaddingTop();
                    canvas.drawRect(left, top, right, bottom, mSelectedLinePaint);
                }
            }
            super.onDraw(canvas);
            if (layout != null) {
                int prevLineNumber = -1;
                canvas.drawRect(getScrollX(), getScrollY(), mGutterWidth + getScrollX(), getScrollY() + getHeight(), mGutterBackgroundPaint);
                int paddingTop = getPaddingTop();
                int max = mLineUtils.getBottomVisibleLine(this);
                int textRight = (mGutterWidth - mIdealMargin / 2) + getScrollX();
                if (codeEditor != null) {
                    int i = mLineUtils.getTopVisibleLine(this);
                    if (i >= 2) {
                        i -= 2;
                    } else {
                        i = 0;
                    }
                    while (i <= max) {
                        int number = codeEditor.getLineForIndex(getLayout().getLineStart(i));
                        if (number != prevLineNumber) {
                            canvas.drawText(Integer.toString(number + 1), textRight,layout.getLineBaseline(i) + paddingTop, mLineNumberPaint);
                        }
                        prevLineNumber = number;
                        ++i;
                    }
                    top = getScrollY();
                    canvas.drawLine(mGutterWidth + getScrollX(), top,mGutterWidth + getScrollX(), top + getHeight(), mLinePaint);
                }
            }
        }
    }

    public void updateGutter() {
        int max = 3;
        if (codeEditor != null && getLayout() != null) {
            TextPaint paint = getLayout().getPaint();
            if (paint != null) {
                mLineNumberDigitCount = Integer.toString(codeEditor.getLineCount()).length();
                int widestNumber = 0;
                float widestWidth = 0.0f;
                for (int i = 0; i <= 9; i++) {
                    float width = paint.measureText(Integer.toString(i));
                    if (width > widestWidth) {
                        widestNumber = i;
                        widestWidth = width;
                    }
                }
                StringBuilder builder = new StringBuilder();
                if (mLineNumberDigitCount >= 3) {
                    max = mLineNumberDigitCount;
                }
                for (int i = 0; i < max; i++) {
                    builder.append(Integer.toString(widestNumber));
                }
                mGutterWidth = (int) paint.measureText(builder.toString());
                mGutterWidth += mIdealMargin;
                if (getPaddingLeft() != mGutterWidth + mIdealMargin) {
                    setPadding(mGutterWidth + mIdealMargin,
                            mIdealMargin, getPaddingRight(), getPaddingBottom());
                }
            }
        } else if (mIdealMargin != getPaddingLeft()) {
            setPadding(mIdealMargin,
                    mIdealMargin, getPaddingRight(), getPaddingBottom());
        }
    }

    protected void syntaxHighlight(Layout layout, Editable editable,int lineHeight, int lineCount, int scrollY, int height) {
        if (layout != null) {
            int topLine = (scrollY / lineHeight) - 10;
            int bottomLine = (((scrollY + height) / lineHeight) + 1) + 10;
            if (topLine < 0) {
                topLine = 0;
            }
            if (bottomLine > layout.getLineCount()) {
                bottomLine = layout.getLineCount();
            }
            if (topLine > layout.getLineCount()) {
                topLine = layout.getLineCount();
            }
            if (bottomLine >= 0 && topLine >= 0) {
                int topLineOffset;
                mTopDirtyLine = topLine;
                mBottomDirtyLine = bottomLine;
                if (topLine >= 0 || topLine >= lineCount) {
                    topLineOffset = layout.getLineStart(topLine);
                } else {
                    topLineOffset = 0;
                }
                final int bottomLineOffset = bottomLine < lineCount
                        ? layout.getLineStart(bottomLine) : layout.getLineStart(lineCount);

                //region PROCESS_HIGHLIGHT

                if (codeEditor.getLanguage() != null) {
                    Matcher m = codeEditor.getLanguage().getSyntaxNumbers().matcher( //Numbers
                            editable.subSequence(topLineOffset, bottomLineOffset));
                    while (m.find()) {
                        editable.setSpan(
                                new SyntaxHighlightSpan(
                                        mSyntaxNumbers,
                                        topLineOffset, bottomLineOffset),
                                m.start() + topLineOffset, m.end() + topLineOffset,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }

                    m = codeEditor.getLanguage().getSyntaxSymbols().matcher( //Symbols
                            editable.subSequence(topLineOffset, bottomLineOffset));
                    while (m.find()) {
                        editable.setSpan(
                                new SyntaxHighlightSpan(
                                        mSyntaxSymbols,
                                        topLineOffset, bottomLineOffset),
                                m.start() + topLineOffset, m.end() + topLineOffset,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }

                    m = codeEditor.getLanguage().getSyntaxBrackets().matcher( //Brackets
                            editable.subSequence(topLineOffset, bottomLineOffset));
                    while (m.find()) {
                        editable.setSpan(
                                new SyntaxHighlightSpan(
                                        mSyntaxBrackets,
                                        topLineOffset, bottomLineOffset),
                                m.start() + topLineOffset, m.end() + topLineOffset,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }

                    m = codeEditor.getLanguage().getSyntaxKeywords().matcher( //Keywords
                            editable.subSequence(topLineOffset, bottomLineOffset));
                    while (m.find()) {
                        editable.setSpan(
                                new SyntaxHighlightSpan(
                                        mSyntaxKeywords,
                                        topLineOffset, bottomLineOffset),
                                m.start() + topLineOffset, m.end() + topLineOffset,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }

                    m = codeEditor.getLanguage().getSyntaxMethods().matcher( //Methods
                            editable.subSequence(topLineOffset, bottomLineOffset));
                    while (m.find()) {
                        editable.setSpan(
                                new SyntaxHighlightSpan(
                                        mSyntaxMethods,
                                        topLineOffset, bottomLineOffset),
                                m.start() + topLineOffset, m.end() + topLineOffset,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }

                    m = codeEditor.getLanguage().getSyntaxStrings().matcher( //Strings
                            editable.subSequence(topLineOffset, bottomLineOffset));
                    while (m.find()) {
                        for (ForegroundColorSpan span : editable.getSpans(
                                m.start() + topLineOffset,
                                m.end() + topLineOffset, ForegroundColorSpan.class)) {
                            editable.removeSpan(span);
                        }
                        editable.setSpan(
                                new SyntaxHighlightSpan(
                                        mSyntaxStrings,
                                        topLineOffset, bottomLineOffset),
                                m.start() + topLineOffset, m.end() + topLineOffset,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }

                    m = codeEditor.getLanguage().getSyntaxComments().matcher( //Comments
                            editable.subSequence(topLineOffset, bottomLineOffset));
                    while (m.find()) {
                        boolean skip = false;
                        for (ForegroundColorSpan span : editable.getSpans(topLineOffset,
                                m.end() + topLineOffset,
                                ForegroundColorSpan.class)) {

                            int spanStart = editable.getSpanStart(span);
                            int spanEnd = editable.getSpanEnd(span);
                            if (((m.start() + topLineOffset >= spanStart && m.start() + topLineOffset
                                    <= spanEnd && m.end() + topLineOffset > spanEnd)
                                    || (m.start() + topLineOffset
                                    >= topLineOffset + spanEnd
                                    && m.start() + topLineOffset <= spanEnd))) {
                                skip = true;
                                break;
                            }

                        }
                        if (!skip) {
                            for (ForegroundColorSpan span : editable.getSpans(
                                    m.start() + topLineOffset, m.end() + topLineOffset,
                                    ForegroundColorSpan.class)) {
                                editable.removeSpan(span);
                            }
                            editable.setSpan(
                                    new SyntaxHighlightSpan(
                                            mSyntaxComments,
                                            topLineOffset, bottomLineOffset),
                                    m.start() + topLineOffset, m.end() + topLineOffset,
                                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                    }
                }

                //endregion PROCESS_HIGHLIGHT
                new Handler().post(new Runnable(){

						@Override
						public void run() {
							invalidateVisibleArea();
						}
					});
            }
        }
    }

    private void invalidateVisibleArea() {
        invalidate(getPaddingLeft(), getScrollY() + getPaddingTop(),
                getWidth(), (getScrollY() + getPaddingTop()) + getHeight());
    }

    /**
     * Алгоритм совпадения скобок.
     *
     * @param pos - позиция курсора.
     */
    protected void checkMatchingBracket(int pos) {
        getText().removeSpan(mOpenBracketSpan);
        getText().removeSpan(mClosedBracketSpan);
        if (mBracketMatching && codeEditor.getLanguage() != null) {
            if (pos > 0 && pos <= getText().length()) {
                char c1 = getText().charAt(pos - 1);
                for (int i = 0; i < codeEditor.getLanguage().getLanguageBrackets().length; i++) {
                    if (codeEditor.getLanguage().getLanguageBrackets()[i] == c1) {
                        char c2 = codeEditor.getLanguage().getLanguageBrackets()[(i + 3) % 6];
                        boolean open = false;
                        if (i <= 2) {
                            open = true;
                        }
                        int k;
                        if (open) {
                            int nob = 1;
                            for (k = pos; k < getText().length(); k++) {
                                if (getText().charAt(k) == c2) {
                                    nob--;
                                }
                                if (getText().charAt(k) == c1) {
                                    nob++;
                                }
                                if (nob == 0) {
                                    showBracket(pos - 1, k);
                                    break;
                                }
                            }
                        } else {
                            int ncb = 1;
                            for (k = pos - 2; k >= 0; k--) {
                                if (getText().charAt(k) == c2) {
                                    ncb--;
                                }
                                if (getText().charAt(k) == c1) {
                                    ncb++;
                                }
                                if (ncb == 0) {
                                    showBracket(k, pos - 1);
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Подсветка найденных скобок.
     *
     * @param i - позиция первой скобки.
     * @param j - позиция второй скобки.
     */
    protected void showBracket(int i, int j) {
        getText().setSpan(mOpenBracketSpan, i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        getText().setSpan(mClosedBracketSpan, j, j + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    protected boolean pinchZoom(MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                zoomPinch = false;
                break;
            case MotionEvent.ACTION_MOVE:
                if (ev.getPointerCount() == 2) {
                    float distance = getDistanceBetweenTouches(ev);
                    if (!zoomPinch) {
                        zoomPinchFactor = textSize / distance;
                        zoomPinch = true;
                        break;
                    }
                    textSize = zoomPinchFactor * distance;
                    validateTextSize();
                    setTextSize(textSize);
                }
                break;
        }
        return zoomPinch;
    }

    //endregion SPAN_METHODS

    //region PINCH_ZOOM

    protected float getDistanceBetweenTouches(MotionEvent ev) {
        float xx = ev.getX(1) - ev.getX(0);
        float yy = ev.getY(1) - ev.getY(0);
        return (float) Math.sqrt(xx * xx + yy * yy);
    }

    protected void validateTextSize() {
        if (textSize < 10) //minimum
            textSize = 10; //minimum
        else if (textSize > 20) //maximum
            textSize = 20; //maximum
    }

    /**
     * Отключаем фокусировку на редакторе, нажатия не будут засчитываться,
     * однако скроллинг будет продолжать работать.
     *
     * @param readOnly - отвечает за режим "Read Only".
     */
    public void setReadOnly(boolean readOnly) {
        setFocusable(!readOnly);
        setFocusableInTouchMode(!readOnly);
    }

    public void setSyntaxHighlight(boolean syntaxHighlight) {
    	syntaxHighlight(getLayout(), getEditableText(), getLineHeight(),getLineCount(), getScrollY(), getHeight());
    }

    /**
     * Метод переключает подсветку скобок.
     *
     * @param bracketMatching - отвечает за включение сопоставления скобок.
     */
    public void setBracketMatching(boolean bracketMatching) {
        mBracketMatching = bracketMatching;
    }

    /**
     * Обновление типа ввода. Если в настройках отключены подсказки на клавиатуре,
     * то они не будут отображаться.
     */
    public void refreshInputType() {
        if (defaultSetting.getImeKeyboard())
            setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE);
        else
            setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE
                    | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
    }

    /**
     * Отображение шрифта в редакторе. Применяется также и к нумерации строк.
     */
    public void refreshTypeface() {
        if (defaultSetting.getCurrentTypeface().equals("droid_sans_mono")) {
            setTypeface(TypefaceManager.get(mContext, TypefaceManager.DROID_SANS_MONO));
        } else if (defaultSetting.getCurrentTypeface().equals("source_code_pro")) {
            setTypeface(TypefaceManager.get(mContext, TypefaceManager.SOURCE_CODE_PRO));
        } else if (defaultSetting.getCurrentTypeface().equals("roboto")) {
            setTypeface(TypefaceManager.get(mContext, TypefaceManager.ROBOTO));
        } else { //if(defaultSetting.getCurrentTypeface().equals("roboto_light"))
            setTypeface(TypefaceManager.get(mContext, TypefaceManager.ROBOTO_LIGHT));
        }
        mLineNumberPaint.setTypeface(getTypeface());
        setPaintFlags(getPaintFlags() | StylePaint.SUBPIXEL_TEXT_FLAG);
    }

    public void setCodeCompletion(boolean enabled) {
            loadSuggestions(); //загружаем список слов
            SymbolsTokenizer mTokenizer = new SymbolsTokenizer();
            setTokenizer(mTokenizer);
            setThreshold(2); //задержка перед отображением
        }

    public void enableUndoRedoStack() { //включаем заполнение стака
        mAddedTextCount = 0;
        mUndoStack = new UndoStack();
        mRedoStack = new UndoStack();
        addTextChangedListener(new TextChangeWatcher());
    }

    public void setPinchZoom() {
            float scaledDensity = getResources().getDisplayMetrics().scaledDensity;
            textSize = getTextSize() / scaledDensity;
			setOnTouchListener(new OnTouchListener(){

					@Override
					public boolean onTouch(View p1, MotionEvent p2) {
						pinchZoom(p2);
						return true;
					}
				});
		}

    public void setInsertBrackets(boolean insertBrackets) {
        mInsertBracket = insertBrackets;
    }

    public void setCursorColor(int color) {
        try {
            // Get the cursor resource id
            Field field = TextView.class.getDeclaredField("mCursorDrawableRes");
            field.setAccessible(true);
            int drawableResId = field.getInt(this);

            // Get the editor
            field = TextView.class.getDeclaredField("mEditor");
            field.setAccessible(true);
            Object editor = field.get(this);

            // Get the drawable and set a color filter
            Drawable drawable =mContext.getDrawable(drawableResId);
            if (drawable != null) {
                drawable.setColorFilter(color, PorterDuff.Mode.SRC_IN);
            }
            Drawable[] drawables = {drawable, drawable};

            // Set the drawables
            field = editor.getClass().getDeclaredField("mCursorDrawable");
            field.setAccessible(true);
            field.set(editor, drawables);
        } catch (Exception e) {
            Logger.error(TAG, e);
        }
    }

    public void insert(CharSequence delta) {
        int selectionStart = getSelectionStart();
        int selectionEnd = getSelectionEnd();
        selectionStart = Math.max(0, selectionStart);
        selectionEnd = Math.max(0, selectionEnd);
        selectionStart = Math.min(selectionStart, selectionEnd);
        selectionEnd = Math.max(selectionStart, selectionEnd);
        try {
            getText().delete(selectionStart, selectionEnd);
            getText().insert(selectionStart, delta);
        } catch (Exception e) {
            Logger.error(TAG, e);
        }
    }

    //endregion DOC_METHODS

    //region METHODS

    public void cut() {
        Editable selectedText = getSelectedText();
        if (selectedText == null || selectedText.toString().equals("")) {
            codeEditor.showToast(mContext.getString(R.string.nothing_to_cut), true);
            Logger.debug(TAG, mContext.getString(R.string.nothing_to_cut));
        } else {
            mClipboardManager.setPrimaryClip(ClipData.newPlainText("CuttedText", selectedText));
            if (getSelectionEnd() > getSelectionStart()) {
                getText().replace(getSelectionStart(), getSelectionEnd(), "");
            } else {
                getText().replace(getSelectionEnd(), getSelectionStart(), "");
            }
        }
    }

    public void copy() {
        Editable selectedText = getSelectedText();
        if (selectedText == null || selectedText.toString().equals("")) {
            codeEditor.showToast(mContext.getString(R.string.nothing_to_copy), true);
            Logger.debug(TAG, mContext.getString(R.string.nothing_to_copy));
        } else {
            mClipboardManager.setPrimaryClip(ClipData.newPlainText("CopiedText", selectedText));
        }
    }

    public void paste() {
        if (mClipboardManager.getPrimaryClip() == null ||
                mClipboardManager.getPrimaryClip().toString().equals("")) {
            codeEditor.showToast(getContext().getString(R.string.nothing_to_paste), true);
            Logger.debug(TAG, getContext().getString(R.string.nothing_to_paste));
        }
        if (!mClipboardManager.hasPrimaryClip()) {
            return;
        }
        if (getSelectionEnd() > getSelectionStart()) {
            getText().replace(getSelectionStart(), getSelectionEnd(),
                    mClipboardManager.getPrimaryClip()
                            .getItemAt(0).coerceToText(mContext));
        } else {
            getText().replace(getSelectionEnd(), getSelectionStart(),
                    mClipboardManager.getPrimaryClip()
                            .getItemAt(0).coerceToText(mContext));
        }
    }

    public void selectLine() {
        int start = Math.min(getSelectionStart(), getSelectionEnd());
        int end = Math.max(getSelectionStart(), getSelectionEnd());
        if (end > start) {
            end--;
        }
        while (end < getText().length() && getText().charAt(end) != '\n') {
            end++;
        }
        while (start > 0 && getText().charAt(start - 1) != '\n') {
            start--;
        }
        setSelection(start, end);
    }

    public void deleteLine() {
        int start = Math.min(getSelectionStart(), getSelectionEnd());
        int end = Math.max(getSelectionStart(), getSelectionEnd());
        if (end > start) {
            end--;
        }
        while (end < getText().length() && getText().charAt(end) != '\n') {
            end++;
        }
        while (start > 0 && getText().charAt(start - 1) != '\n') {
            start--;
        }
        getEditableText().delete(start, end);
    }

    public void duplicateLine() {
        int start = Math.min(getSelectionStart(), getSelectionEnd());
        int end = Math.max(getSelectionStart(), getSelectionEnd());
        if (end > start) {
            end--;
        }
        while (end < getText().length() && getText().charAt(end) != '\n') {
            end++;
        }
        while (start > 0 && getText().charAt(start - 1) != '\n') {
            start--;
        }
        getEditableText().insert(end, "\n" +
                getText().subSequence(start, end).toString());
    }

    public void undo() {
        TextChange textChange = mUndoStack.pop();
        if (textChange == null) {
            Logger.debug(TAG, mContext.getString(R.string.nothing_to_undo));
            codeEditor.showToast(mContext.getString(R.string.nothing_to_undo), true);
        } else if (textChange.start >= 0) {
            isDoingUndoRedo = true;
            if (textChange.start < 0) {
                textChange.start = 0;
            }
            if (textChange.start > getText().length()) {
                textChange.start = getText().length();
            }
            int end = textChange.start + textChange.newText.length();
            if (end < 0) {
                end = 0;
            }
            if (end > getText().length()) {
                end = getText().length();
            }
            getText().replace(textChange.start, end, textChange.oldText);
            Selection.setSelection(getText(), textChange.start + textChange.oldText.length());
            mRedoStack.push(textChange);
            isDoingUndoRedo = false;
        } else {
            Logger.error(TAG, "undo(): unknown error", null);
            mUndoStack.clear();
        }
    }

    public void redo() {
        TextChange textChange = mRedoStack.pop();
        if (textChange == null) {
            Logger.debug(TAG, mContext.getString(R.string.nothing_to_redo));
            codeEditor.showToast(mContext.getString(R.string.nothing_to_redo), true);
        } else if (textChange.start >= 0) {
            isDoingUndoRedo = true;
            getText().replace(textChange.start,
                    textChange.start + textChange.oldText.length(), textChange.newText);
            Selection.setSelection(getText(), textChange.start + textChange.newText.length());
            mUndoStack.push(textChange);
            isDoingUndoRedo = false;
        } else {
            Logger.error(TAG, "redo(): unknown error", null);
            mUndoStack.clear();
        }
    }

    public void gotoLine(int toLine) {
        int realLine = toLine - 1;
        if (realLine == -1) {
            codeEditor.showToast(mContext.getString(R.string.gotoLine_above_than_0), true);
        } else if (realLine < codeEditor.getLineCount()) {
            setSelection(codeEditor.getIndexForStartOfLine(realLine));
        } else {
            codeEditor.showToast(mContext.getString(R.string.gotoLine_not_exists), true);
        }
    }

    public void find(String what, boolean matchCase, boolean regex, boolean wordOnly, Editable e) {
        Pattern pattern;
        if (regex) {
            if (matchCase) {
                pattern = Pattern.compile(what);
            } else {
                pattern = Pattern.compile(what,
                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            }
        } else {
            if (wordOnly) {
                if (matchCase) {
                    pattern = Pattern.compile("\\s" + what + "\\s");
                } else {
                    pattern = Pattern.compile("\\s" + Pattern.quote(what) + "\\s",
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                }
            } else {
                if (matchCase) {
                    pattern = Pattern.compile(Pattern.quote(what));
                } else {
                    pattern = Pattern.compile(Pattern.quote(what),
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                }
            }
        }
        //Очищаем background-спаны, потому что будем накладывать новые
        clearSpans(e, false, true, false);
        for (Matcher m = pattern.matcher(e); m.find(); ) {
            e.setSpan(new BackgroundColorSpan(mColorSearchSpan.data),
                    m.start(),
                    m.end(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    public void replaceAll(String what, String with) {
        //Очищаем спаны из-за смены текста
        clearSpans(getEditableText(), false, true, true);
        setText(getText().toString().replaceAll(what, with));
    }

    protected final class TextChangeWatcher implements TextWatcher {

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            mAddedTextCount -= count;
            mOldText = s.subSequence(start, start + count).toString();
            updateDocumentBeforeTextChanged(start, count);
            updateUndoRedoBeforeTextChanged(s, start, count);
            abortFling();
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            mAddedTextCount += count;
            mNewText = s.subSequence(start, start + count).toString();
            generalOnTextChanged(start, count);
            updateDocumentOnTextChanged(s, start, count);
            updateUndoRedoOnTextChanged(s, start, count);
            mOldText = "";
            mNewText = "";
            onPopupChangePosition();
        }

        @Override
        public void afterTextChanged(Editable editable) {
            mAddedTextCount = 0;
            clearSpans(editable, false, true, true);
            syntaxHighlight(getLayout(), getEditableText(), getLineHeight(), getLineCount(), getScrollY(), getHeight());
        }
    }
}
