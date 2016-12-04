/*
 *  Kontalk Java client
 *  Copyright (C) 2016 Kontalk Devteam <devteam@kontalk.org>
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

import javax.swing.Icon;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import com.alee.extended.image.WebImage;
import com.alee.laf.button.WebButton;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.text.WebTextField;
import org.kontalk.util.Tr;

/**
 * A search bar to search for text in contact, chat and message lists.
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
final class SearchPanel extends WebPanel {
    private final WebTextField mSearchField;

    SearchPanel(final ListView[] lists, final ChatView chatView) {
        mSearchField = new WebTextField();
        mSearchField.setInputPrompt(Tr.tr("Search…"));
        mSearchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                this.filterList();
            }
            @Override
            public void removeUpdate(DocumentEvent e) {
                this.filterList();
            }
            @Override
            public void changedUpdate(DocumentEvent e) {
                this.filterList();
            }
            private void filterList() {
                String searchText = mSearchField.getText().toLowerCase();
                for (ListView list : lists)
                    list.filterItems(searchText);
                chatView.filterCurrentChat(searchText);
            }
        });
        mSearchField.setLeadingComponent(new WebImage(Utils.getIcon("ic_ui_search.png")));
        Icon clearIcon = Utils.getIcon("ic_ui_clear.png");
        WebButton clearSearchButton = new WebButton(clearIcon);
        clearSearchButton.setUndecorated(true);
        clearSearchButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                mSearchField.clear();
            }
        });
        mSearchField.setTrailingComponent(clearSearchButton);
        this.add(mSearchField, BorderLayout.CENTER);
    }
}
