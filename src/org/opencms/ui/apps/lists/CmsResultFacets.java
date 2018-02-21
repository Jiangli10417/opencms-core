/*
 * This library is part of OpenCms -
 * the Open Source Content Management System
 *
 * Copyright (c) Alkacon Software GmbH & Co. KG (http://www.alkacon.com)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * For further information about Alkacon Software, please see the
 * company website: http://www.alkacon.com
 *
 * For further information about OpenCms, please see the
 * project website: http://www.opencms.org
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.opencms.ui.apps.lists;

import org.opencms.file.CmsObject;
import org.opencms.jsp.search.controller.I_CmsSearchControllerFacetField;
import org.opencms.jsp.search.controller.I_CmsSearchControllerFacetRange;
import org.opencms.jsp.search.result.CmsSearchResultWrapper;
import org.opencms.main.CmsLog;
import org.opencms.relations.CmsCategory;
import org.opencms.relations.CmsCategoryService;
import org.opencms.search.solr.CmsSolrResultList;
import org.opencms.ui.A_CmsUI;
import org.opencms.ui.CmsVaadinUtils;
import org.opencms.ui.apps.Messages;
import org.opencms.ui.components.OpenCmsTheme;
import org.opencms.util.CmsStringUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.FacetField.Count;
import org.apache.solr.client.solrj.response.RangeFacet;

import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;
import com.vaadin.ui.Component;
import com.vaadin.ui.GridLayout;
import com.vaadin.v7.ui.Label;
import com.vaadin.ui.Panel;
import com.vaadin.ui.UI;
import com.vaadin.v7.ui.VerticalLayout;
import com.vaadin.ui.themes.ValoTheme;

/**
 * Displays search result facets.<p>
 */
public class CmsResultFacets extends VerticalLayout {

    /** The logger for this class. */
    private static final Log LOG = CmsLog.getLog(CmsResultFacets.class.getName());

    /** The serial version id. */
    private static final long serialVersionUID = 7190928063356086124L;

    /** The list manager instance. */
    private CmsListManager m_manager;

    /** The selected field facets. */
    private Map<String, List<String>> m_selectedFieldFacets;

    /** The selected folders. */
    private List<String> m_selectedFolders;

    /** The selected range facets. */
    private Map<String, List<String>> m_selectedRangeFacets;

    /** The use full category paths flag. */
    private boolean m_useFullPathCategories;

    /**
     * Constructor.<p>
     *
     * @param manager the list manager instance
     */
    public CmsResultFacets(CmsListManager manager) {
        m_manager = manager;
        m_selectedFieldFacets = new HashMap<String, List<String>>();
        m_selectedRangeFacets = new HashMap<String, List<String>>();
        m_selectedFolders = new ArrayList<String>();
        m_useFullPathCategories = true;
        addStyleName("v-scrollable");
        setMargin(true);
        setSpacing(true);
    }

    /**
     * Displays the result facets.<p>
     *
     * @param solrResultList the search result
     * @param resultWrapper the result wrapper
     */
    public void displayFacetResult(CmsSolrResultList solrResultList, CmsSearchResultWrapper resultWrapper) {

        removeAllComponents();
        Component categories = prepareCategoryFacets(solrResultList, resultWrapper);
        if (categories != null) {
            addComponent(categories);
        }
        Component folders = prepareFolderFacets(solrResultList, resultWrapper);
        if (folders != null) {
            addComponent(folders);
        }
        Component dates = prepareDateFacets(solrResultList, resultWrapper);
        if (dates != null) {
            addComponent(dates);
        }
    }

    /**
     * Resets the selected facets.<p>
     */
    public void resetFacets() {

        m_selectedFieldFacets.clear();
        m_selectedRangeFacets.clear();
    }

    /**
     * Returns the selected field facets.<p>
     *
     * @return the selected field facets
     */
    protected Map<String, List<String>> getSelectedFieldFacets() {

        return m_selectedFieldFacets;
    }

    /**
     * Returns the selected range facets.<p>
     *
     * @return the selected range facets
     */
    protected Map<String, List<String>> getSelectedRangeFactes() {

        return m_selectedRangeFacets;
    }

    /**
     * Selects the given field facet.<p>
     *
     * @param field the field name
     * @param value the value
     */
    void selectFieldFacet(String field, String value) {

        m_selectedFieldFacets.clear();
        m_selectedRangeFacets.clear();
        m_selectedFieldFacets.put(field, Collections.singletonList(value));
        m_manager.search(m_selectedFieldFacets, m_selectedRangeFacets);
    }

    /**
     * Selects the given range facet.<p>
     *
     * @param field the field name
     * @param value the value
     */
    void selectRangeFacet(String field, String value) {

        m_selectedFieldFacets.clear();
        m_selectedRangeFacets.clear();
        m_selectedRangeFacets.put(field, Collections.singletonList(value));
        m_manager.search(m_selectedFieldFacets, m_selectedRangeFacets);
    }

    /**
     * Filters the available folder facets.<p>
     *
     * @param folderFacets the folder facets
     *
     * @return the filtered facets
     */
    private Collection<Count> filterFolderFacets(Collection<Count> folderFacets) {

        String siteRoot = A_CmsUI.getCmsObject().getRequestContext().getSiteRoot();
        if (!siteRoot.endsWith("/")) {
            siteRoot += "/";
        }
        Collection<Count> result = new ArrayList<Count>();
        for (Count value : folderFacets) {
            if (value.getName().startsWith(siteRoot) && (value.getName().length() > siteRoot.length())) {
                if (m_selectedFolders.isEmpty()) {
                    result.add(value);
                } else {
                    for (String folder : m_selectedFolders) {
                        if (value.getName().startsWith(folder)) {
                            result.add(value);
                            break;
                        }
                    }
                }
            }
        }
        return result;
    }

    /**
     * Returns the label for the given category.<p>
     *
     * @param categoryPath the category
     *
     * @return the label
     */
    private String getCategoryLabel(String categoryPath) {

        CmsObject cms = A_CmsUI.getCmsObject();
        String result = "";
        if (CmsStringUtil.isEmptyOrWhitespaceOnly(categoryPath)) {
            return result;
        }
        Locale locale = UI.getCurrent().getLocale();
        CmsCategoryService catService = CmsCategoryService.getInstance();

        try {
            if (m_useFullPathCategories) {
                //cut last slash
                categoryPath = categoryPath.substring(0, categoryPath.length() - 1);

                String currentPath = "";
                boolean isFirst = true;
                for (String part : categoryPath.split("/")) {
                    currentPath += part + "/";
                    CmsCategory cat = catService.localizeCategory(
                        cms,
                        catService.readCategory(cms, currentPath, "/"),
                        locale);
                    if (!isFirst) {
                        result += "  /  ";
                    } else {
                        isFirst = false;
                    }
                    result += cat.getTitle();
                }

            } else {

                CmsCategory cat = catService.localizeCategory(
                    cms,
                    catService.readCategory(cms, categoryPath, "/"),
                    locale);
                result = cat.getTitle();
            }
        } catch (Exception e) {
            LOG.error("Error reading category " + categoryPath + ".", e);
        }
        return result;
    }

    /**
     * Returns the label for the given folder.<p>
     *
     * @param path The folder path
     *
     * @return the label
     */
    private String getFolderLabel(String path) {

        CmsObject cms = A_CmsUI.getCmsObject();
        return cms.getRequestContext().removeSiteRoot(path);
    }

    /**
     * Prepares the category facets for the given search result.<p>
     *
     * @param solrResultList the search result list
     * @param resultWrapper the result wrapper
     *
     * @return the category facets component
     */
    private Component prepareCategoryFacets(CmsSolrResultList solrResultList, CmsSearchResultWrapper resultWrapper) {

        FacetField categoryFacets = solrResultList.getFacetField(CmsListManager.FIELD_CATEGORIES);
        I_CmsSearchControllerFacetField facetController = resultWrapper.getController().getFieldFacets().getFieldFacetController().get(
            CmsListManager.FIELD_CATEGORIES);
        if ((categoryFacets != null) && (categoryFacets.getValueCount() > 0)) {
            VerticalLayout catLayout = new VerticalLayout();
            for (final Count value : categoryFacets.getValues()) {
                Button cat = new Button(getCategoryLabel(value.getName()) + " (" + value.getCount() + ")");
                cat.addStyleName(ValoTheme.BUTTON_TINY);
                cat.addStyleName(ValoTheme.BUTTON_BORDERLESS);
                Boolean selected = facetController.getState().getIsChecked().get(value.getName());
                if ((selected != null) && selected.booleanValue()) {
                    cat.addStyleName(ValoTheme.LABEL_BOLD);
                }
                cat.addClickListener(new ClickListener() {

                    private static final long serialVersionUID = 1L;

                    public void buttonClick(ClickEvent event) {

                        selectFieldFacet(CmsListManager.FIELD_CATEGORIES, value.getName());
                    }
                });
                catLayout.addComponent(cat);
            }
            Panel catPanel = new Panel(CmsVaadinUtils.getMessageText(Messages.GUI_LISTMANAGER_FACET_CATEGORIES_0));
            catPanel.setContent(catLayout);
            return catPanel;
        } else {
            return null;
        }
    }

    /**
     * Prepares the date facets for the given search result.<p>
     *
     * @param solrResultList the search result list
     * @param resultWrapper the result wrapper
     *
     * @return the date facets component
     */
    private Component prepareDateFacets(CmsSolrResultList solrResultList, CmsSearchResultWrapper resultWrapper) {

        RangeFacet<?, ?> dateFacets = resultWrapper.getRangeFacet().get(CmsListManager.FIELD_DATE_FACET_NAME);
        I_CmsSearchControllerFacetRange facetController = resultWrapper.getController().getRangeFacets().getRangeFacetController().get(
            CmsListManager.FIELD_DATE_FACET_NAME);
        if ((dateFacets != null) && (dateFacets.getCounts().size() > 0)) {
            GridLayout dateLayout = new GridLayout();
            dateLayout.setWidth("100%");
            dateLayout.setColumns(6);
            String currentYear = null;
            int row = -2;
            for (final RangeFacet.Count value : dateFacets.getCounts()) {
                String[] dateParts = value.getValue().split("-");
                if (!dateParts[0].equals(currentYear)) {
                    row += 2;
                    dateLayout.setRows(row + 2);
                    currentYear = dateParts[0];
                    Label year = new Label(currentYear);
                    year.addStyleName(OpenCmsTheme.PADDING_HORIZONTAL);
                    dateLayout.addComponent(year, 0, row, 5, row);
                    row++;
                }
                int month = Integer.parseInt(dateParts[1]) - 1;

                Button date = new Button(CmsListManager.MONTHS[month] + " (" + value.getCount() + ")");
                date.addStyleName(ValoTheme.BUTTON_TINY);
                date.addStyleName(ValoTheme.BUTTON_BORDERLESS);
                Boolean selected = facetController.getState().getIsChecked().get(value.getValue());
                if ((selected != null) && selected.booleanValue()) {
                    date.addStyleName(ValoTheme.LABEL_BOLD);
                }
                date.addClickListener(new ClickListener() {

                    private static final long serialVersionUID = 1L;

                    public void buttonClick(ClickEvent event) {

                        selectRangeFacet(CmsListManager.FIELD_DATE_FACET_NAME, value.getValue());
                    }
                });
                int targetColumn;
                int targetRow;
                if (month < 6) {
                    targetColumn = month;
                    targetRow = row;
                } else {
                    targetColumn = month - 6;
                    targetRow = row + 1;
                    dateLayout.setRows(row + 2);
                }
                dateLayout.addComponent(date, targetColumn, targetRow);
            }
            Panel datePanel = new Panel(CmsVaadinUtils.getMessageText(Messages.GUI_LISTMANAGER_FACET_DATE_0));
            datePanel.setContent(dateLayout);
            return datePanel;
        } else {
            return null;
        }
    }

    /**
     * Prepares the folder facets for the given search result.<p>
     *
     * @param solrResultList the search result list
     * @param resultWrapper the result wrapper
     *
     * @return the folder facets component
     */
    private Component prepareFolderFacets(CmsSolrResultList solrResultList, CmsSearchResultWrapper resultWrapper) {

        FacetField folderFacets = solrResultList.getFacetField(CmsListManager.FIELD_PARENT_FOLDERS);
        I_CmsSearchControllerFacetField facetController = resultWrapper.getController().getFieldFacets().getFieldFacetController().get(
            CmsListManager.FIELD_PARENT_FOLDERS);
        if ((folderFacets != null) && (folderFacets.getValueCount() > 0)) {
            VerticalLayout folderLayout = new VerticalLayout();
            for (final Count value : filterFolderFacets(folderFacets.getValues())) {
                Button folder = new Button(getFolderLabel(value.getName()) + " (" + value.getCount() + ")");
                folder.addStyleName(ValoTheme.BUTTON_TINY);
                folder.addStyleName(ValoTheme.BUTTON_BORDERLESS);
                Boolean selected = facetController.getState().getIsChecked().get(value.getName());
                if ((selected != null) && selected.booleanValue()) {
                    folder.addStyleName(ValoTheme.LABEL_BOLD);
                }
                folder.addClickListener(new ClickListener() {

                    private static final long serialVersionUID = 1L;

                    public void buttonClick(ClickEvent event) {

                        selectFieldFacet(CmsListManager.FIELD_PARENT_FOLDERS, value.getName());
                    }
                });
                folderLayout.addComponent(folder);
            }
            Panel folderPanel = new Panel(CmsVaadinUtils.getMessageText(Messages.GUI_LISTMANAGER_FACET_FOLDERS_0));
            folderPanel.setContent(folderLayout);
            return folderPanel;
        } else {
            return null;
        }
    }

}
