/*
 *  Kontalk Java client
 *  Copyright (C) 2014 Kontalk Devteam <devteam@kontalk.org>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kontalk.view;

import com.alee.extended.filechooser.FilesSelectionListener;
import com.alee.extended.filechooser.WebFileChooserField;
import com.alee.extended.panel.GroupPanel;
import com.alee.laf.button.WebButton;
import com.alee.laf.checkbox.WebCheckBox;
import com.alee.laf.label.WebLabel;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.rootpane.WebDialog;
import com.alee.laf.separator.WebSeparator;
import com.alee.laf.text.WebPasswordField;
import com.alee.utils.swing.DocumentChangeListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.util.EnumMap;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.kontalk.misc.KonException;
import org.kontalk.system.AccountImporter;
import org.kontalk.system.Account;
import org.kontalk.util.Tr;

/**
 * Wizard-like dialog for importing new key files.
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
final class ImportDialog extends WebDialog {
    private static final Logger LOGGER = Logger.getLogger(ImportDialog.class.getName());

    private static enum ImportPage {INTRO, SETTINGS, RESULT};
    private static enum Direction {BACK, FORTH};

    private final EnumMap<ImportPage, ImportPanel> mPanels;
    private final WebButton mBackButton;
    private final WebButton mNextButton;
    private final WebButton mCancelButton;
    private final WebButton mFinishButton;

    private final View mView;
    private final boolean mConnect;

    private final ResultPanel mResultPanel;

    private ImportPage mCurrentPage;

    // exchanged between panels
    private String mZipPath = "";
    private char[] mPasswd = {};

    ImportDialog(final View view, final boolean connect) {
        mView = view;
        mConnect = connect;

        this.setTitle(Tr.tr("Import Wizard"));
        this.setSize(420, 300);

        this.setResizable(false);
        this.setModal(true);

        this.setLayout(new BorderLayout(View.GAP_DEFAULT, View.GAP_DEFAULT));

        // buttons
        mBackButton = new WebButton(Tr.tr("Back"));
        mBackButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                mResultPanel.mayAbort();
                ImportDialog.this.switchPage(Direction.BACK);
            }
        });
        mNextButton = new WebButton(Tr.tr("Next"));
        mNextButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ImportDialog.this.switchPage(Direction.FORTH);
            }
        });
        mCancelButton = new WebButton(Tr.tr("Cancel"));
        mCancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                mResultPanel.mayAbort();
                ImportDialog.this.dispose();
            }
        });
        mFinishButton = new WebButton(Tr.tr("Done"));
        mFinishButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                mPanels.get(mCurrentPage).onNext();
            }
        });
        mFinishButton.setVisible(false);

        GroupPanel buttonPanel = new GroupPanel(mBackButton, mNextButton,
                mCancelButton, mFinishButton);
        buttonPanel.setLayout(new FlowLayout(FlowLayout.TRAILING));
        this.add(buttonPanel, BorderLayout.SOUTH);

        // panels
        mPanels = new EnumMap<>(ImportPage.class);
        mPanels.put(ImportPage.INTRO, new IntroPanel());
        mPanels.put(ImportPage.SETTINGS, new SettingsPanel());
        mResultPanel = new ResultPanel();
        mPanels.put(ImportPage.RESULT, mResultPanel);

        this.setPage(ImportPage.INTRO);
    }

    private void switchPage(Direction dir) {
        int step = dir == Direction.BACK ? -1 : +1;
        ImportPage[] pages = ImportPage.values();
        ImportPage newPage = pages[mCurrentPage.ordinal() + step];
        ImportPanel oldPanel = mPanels.get(mCurrentPage);
        if (dir == Direction.FORTH)
            oldPanel.onNext();
        this.remove(oldPanel);
        this.setPage(newPage);
    }

    private void setPage(ImportPage newPage) {
        mCurrentPage = newPage;
        ImportPanel newPanel = mPanels.get(mCurrentPage);
        newPanel.onShow();
        this.add(newPanel, BorderLayout.CENTER);
        // swing is messy again
        this.repaint();
    }

    private abstract class ImportPanel extends WebPanel {

        abstract protected void onShow();

        protected void onNext() {};
    }

    private class IntroPanel extends ImportPanel {

        IntroPanel() {
            GroupPanel groupPanel = new GroupPanel(View.GAP_DEFAULT, false);
            groupPanel.setMargin(View.MARGIN_BIG);

            groupPanel.add(new WebLabel(Tr.tr("Get Started")).setBoldFont());
            groupPanel.add(new WebSeparator(true, true));

            // html tag for word wrap
            String text = "<html>"+
                    Tr.tr("Welcome to the import wizard.")+" "
                    +Tr.tr("To use the Kontalk desktop client you need an existing account.")+" "
                    +Tr.tr("Please export the key files from your Android device and select them on the next page.")
                    +"</html>";
            groupPanel.add(new WebLabel(text));

            this.add(groupPanel);
        }

        @Override
        protected void onShow() {
            mBackButton.setVisible(false);
            mNextButton.setEnabled(true);
        }
    }

    private class SettingsPanel extends ImportPanel {

        private final WebFileChooserField mZipFileChooser;
        private final WebPasswordField mPassField;

        SettingsPanel() {
            GroupPanel groupPanel = new GroupPanel(View.GAP_DEFAULT, false);
            groupPanel.setMargin(View.MARGIN_BIG);

            groupPanel.add(new WebLabel(Tr.tr("Setup")).setBoldFont());
            groupPanel.add(new WebSeparator(true, true));

            // file chooser for key files
            groupPanel.add(new WebLabel(Tr.tr("Zip archive containing personal key:")));

            mZipFileChooser = createFileChooser(".kontalk-keys.zip");
            mZipFileChooser.addSelectedFilesListener(new FilesSelectionListener() {
                @Override
                public void selectionChanged(List<File> files) {
                        SettingsPanel.this.checkNextButton();
                }
            });
            groupPanel.add(mZipFileChooser);
            groupPanel.add(new WebSeparator(true, true));

            // text field for passphrase
            groupPanel.add(new WebLabel(Tr.tr("Decryption password for key:")));
            mPassField = new WebPasswordField(42);
            mPassField.setInputPrompt(Tr.tr("Enter password…"));
            mPassField.setHideInputPromptOnFocus(false);
            mPassField.getDocument().addDocumentListener(new DocumentChangeListener() {
                @Override
                public void documentChanged(DocumentEvent e) {
                    SettingsPanel.this.checkNextButton();
                }
            });
            groupPanel.add(mPassField);

            WebCheckBox showPasswordBox = new WebCheckBox(Tr.tr("Show password"));
            showPasswordBox.addItemListener(new ItemListener() {
                @Override
                public void itemStateChanged(ItemEvent e) {
                    boolean selected = e.getStateChange() == ItemEvent.SELECTED;
                    mPassField.setEchoChar(selected ? (char)0 : '*');
                }
            });
            groupPanel.add(showPasswordBox);

            this.add(groupPanel);
        }

        private void checkNextButton() {
            mNextButton.setEnabled(!mZipFileChooser.getSelectedFiles().isEmpty() &&
                    !String.valueOf(mPassField.getPassword()).isEmpty());
        }

        @Override
        protected void onShow() {
            mBackButton.setVisible(true);
            mNextButton.setVisible(true);
            mCancelButton.setVisible(true);
            mFinishButton.setVisible(false);
            this.checkNextButton();
        }

        @Override
        protected void onNext() {
            if (!mZipFileChooser.getSelectedFiles().isEmpty())
                mZipPath = mZipFileChooser.getSelectedFiles().get(0).getAbsolutePath();
            mPasswd = mPassField.getPassword();
        }
    }

    private class ResultPanel extends ImportPanel implements Observer {

        private final AccountImporter mImporter;

        private final WebLabel mResultLabel;
        private final WebLabel mErrorLabel;
        private final ComponentUtils.PassPanel mPassPanel;

        private boolean mWaiting = false;

        ResultPanel() {
            mImporter = new AccountImporter(this);

            GroupPanel groupPanel = new GroupPanel(View.GAP_DEFAULT, false);
            groupPanel.setMargin(View.MARGIN_BIG);

            groupPanel.add(new WebLabel(Tr.tr("Import results")).setBoldFont());
            groupPanel.add(new WebSeparator(true, true));

            mResultLabel = new WebLabel();
            groupPanel.add(mResultLabel);
            mErrorLabel = new WebLabel();
            groupPanel.add(mErrorLabel);

            mPassPanel = new ComponentUtils.PassPanel(false) {
                @Override
                void onValidInput() {
                    mFinishButton.setEnabled(true);
                }
                @Override
                void onInvalidInput() {
                    mFinishButton.setEnabled(false);
                }
            };
            groupPanel.add(mPassPanel);

            this.add(groupPanel);
        }

        @Override
        protected void onShow() {
            mNextButton.setVisible(false);
            mCancelButton.setVisible(true);
            this.importAccount();
        }

        private void importAccount() {
            if (mZipPath.isEmpty()) {
                LOGGER.warning("no zip file path");
                return;
            }

            mResultLabel.setText(Tr.tr("Waiting..."));
            mWaiting = true;
            mImporter.fromZipFile(mZipPath, mPasswd);
        }

        @Override
        public void update(Observable o, final Object arg) {
            if (SwingUtilities.isEventDispatchThread()) {
                this.updateOnEDT(arg);
                return;
            }
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    ResultPanel.this.updateOnEDT(arg);
                }
            });
        }

        private void updateOnEDT(Object arg) {
            if (arg == null) {
                this.onResult(null);
            } else if (arg instanceof KonException) {
                this.onResult((KonException) arg);
            } else {
                LOGGER.warning("unexpected argument: "+arg);
            }
        }

        private void onResult(KonException ex) {
            mWaiting = false;

            String errorText = null;
            if (ex != null) {
                errorText = Utils.getErrorText(ex);
            } else {
                mCancelButton.setVisible(false);
                mFinishButton.setVisible(true);
            }

            mPassPanel.setVisible(ex == null);

            String result = ex == null ? Tr.tr("Success!") : Tr.tr("Error");
            mResultLabel.setText(Tr.tr("Import process finished with:")+" "+result);
            mErrorLabel.setText(errorText == null ?
                    "" :
                    "<html>"+Tr.tr("Error description:")+" \n\n"+errorText+"</html>");
        }

        private void mayAbort() {
            if (!mWaiting)
                return;

            mImporter.abort();
            mWaiting = false;
        }

        @Override
        protected void onNext() {
            Optional<char[]> optNewPass = mPassPanel.getNewPassword();
            if (optNewPass.isPresent() && optNewPass.get().length > 0) {
                try {
                    Account.getInstance().setPassword(new char[0], optNewPass.get());
                } catch (KonException ex) {
                    LOGGER.log(Level.WARNING, "can't set password", ex);
                    return;
                }
            }
            ImportDialog.this.dispose();
            if (mConnect)
                mView.getControl().connect();
        }
    }

    private static WebFileChooserField createFileChooser(String path) {
        final WebFileChooserField fileChooser = new WebFileChooserField();
        fileChooser.setMultiSelectionEnabled(false);
        fileChooser.setShowFileShortName(false);
        fileChooser.setShowRemoveButton(false);
        fileChooser.getWebFileChooser().setFileFilter(new FileNameExtensionFilter(Tr.tr("Zip archive"), "zip"));
        File file = new File(path);
        if (file.exists()) {
            fileChooser.setSelectedFile(file);
        } else {
            fileChooser.setBorderColor(Color.RED);
        }

        if (file.getParentFile() != null && file.getParentFile().exists())
            fileChooser.getWebFileChooser().setCurrentDirectory(file.getParentFile());

        fileChooser.addSelectedFilesListener(new FilesSelectionListener() {
            @Override
            public void selectionChanged(List<File> files) {
                for (File file : files) {
                    if (file.exists()) {
                        fileChooser.setBorderColor(Color.BLACK);
                    }
                }
            }
        });

        return fileChooser;
    }
}
