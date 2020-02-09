package com.github.meng.editor.processor.utils;

import android.os.Environment;

public interface ITextProcessorSetting {

    public int getMaxTabsCount();

    public boolean getFullScreenMode();

    public boolean getConfirmExit();

    public boolean getResumeSession();

    public boolean getDisableSwipeGesture();

    public boolean getImeKeyboard();

    public String getCurrentTypeface();

    public int getFontSize();

    public boolean getWrapContent();

    public boolean getBracketMatching();

    public String getWorkingFolder() ;

    public void setWorkingFolder(String newWorkingFolder);

    public String getSortMode();

    public boolean getCreatingFilesAndFolders() ;

    public boolean getHighlightCurrentLine() ;

    public boolean getCodeCompletion();

    public boolean getShowHiddenFiles();

    public boolean getInsertBracket();

    public boolean getExtendedKeyboard();
}
