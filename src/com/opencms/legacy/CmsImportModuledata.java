/*
* File   : $Source: /alkacon/cvs/opencms/src/com/opencms/legacy/Attic/CmsImportModuledata.java,v $
* Date   : $Date: 2004/02/26 16:14:30 $
* Version: $Revision: 1.3 $
*
* This library is part of OpenCms -
* the Open Source Content Mananagement System
*
 * This library is part of OpenCms -
 * the Open Source Content Mananagement System
 *
 * Copyright (C) 2002 - 2003 Alkacon Software (http://www.alkacon.com)
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

package com.opencms.legacy;

import org.opencms.file.CmsObject;
import org.opencms.i18n.CmsEncoder;
import org.opencms.importexport.CmsImport;
import org.opencms.importexport.I_CmsImport;
import org.opencms.main.CmsException;
import org.opencms.main.I_CmsConstants;
import org.opencms.main.OpenCms;
import org.opencms.report.I_CmsReport;
import org.opencms.util.CmsUUID;

import com.opencms.defaults.master.CmsMasterContent;
import com.opencms.defaults.master.CmsMasterDataSet;
import com.opencms.defaults.master.CmsMasterMedia;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;
import java.util.zip.ZipEntry;

import org.dom4j.Document;
import org.dom4j.Element;

/**
 * Holds the functionaility to import resources from the filesystem
 * or a zip file into the OpenCms COS.
 *
 * @author Alexander Kandzior (a.kandzior@alkacon.com)
 * @author Michael Emmerich (m.emmerich@alkacon.com) 
 * @author Thomas Weckert (t.weckert@alkacon.com)
 * 
 * @version $Revision: 1.3 $ $Date: 2004/02/26 16:14:30 $
 */
public class CmsImportModuledata extends CmsImport implements Serializable {

    /**
     * Constructs a new import object which imports the module data from an OpenCms 
     * export zip file or a folder in the "real" file system.<p>
     *
     * @param cms the current cms object
     * @param importFile the file or folder to import from
     * @param importPath the path in the cms VFS to import into
     * @param report a report object to output the progress information to
     */
    public CmsImportModuledata(CmsObject cms, String importFile, String importPath, I_CmsReport report) {
        // set member variables
        m_cms = cms;
        m_importFile = importFile;
        m_importPath = importPath;
        m_report = report;
        m_importingChannelData = true;
        // try to get all import implementations
         // This has only made once.
         if (m_ImportImplementations == null) {
             m_ImportImplementations=OpenCms.getRegistry().getImportClasses();
         }     
     }

    /**
     * Imports the moduledata and writes them to the cms even if there already exist 
     * conflicting files.<p>
     * @throws CmsException in case something goes wrong
     */
    public void importResources() throws CmsException {
        // initialize the import
        openImportFile();
        m_report.println("Import Version "+m_importVersion, I_CmsReport.C_FORMAT_NOTE);
        try {
            // first import the channels
            m_report.println(m_report.key("report.import_channels_begin"), I_CmsReport.C_FORMAT_HEADLINE);
            //importAllResources(null, null, null, null, null);
            // now find the correct import implementation    
            m_cms.getRequestContext().saveSiteRoot();
            m_cms.setContextToCos();     
            Iterator i=m_ImportImplementations.iterator();
                while (i.hasNext()) {
                    I_CmsImport imp=((I_CmsImport)i.next());
                    if (imp.getVersion()==m_importVersion) {
                        // this is the correct import version, so call it for the import process
                        imp.importResources(m_cms, m_importPath, m_report, 
                                        m_digest, m_importResource, m_importZip, m_docXml, null, null, null, null, null);
                        break;                    
                     }
                 }   
            m_cms.getRequestContext().restoreSiteRoot();
            m_report.println(m_report.key("report.import_channels_end"), I_CmsReport.C_FORMAT_HEADLINE);
 
            // now import the moduledata
            m_report.println(m_report.key("report.import_moduledata_begin"), I_CmsReport.C_FORMAT_HEADLINE);
            importModuleMasters();
            m_report.println(m_report.key("report.import_moduledata_end"), I_CmsReport.C_FORMAT_HEADLINE);
        } catch (CmsException e) {
            m_report.println(e);
            throw e;
        } finally {
            // close the import file
            closeImportFile();
        }
    }

    /**
     * Gets the available modules in the current system
     * and imports the data for existing modules.<p>
     * @throws CmsException in case something goes wrong
     */
    public void importModuleMasters() throws CmsException {
        // get all available modules in this system
        Hashtable moduleExportables = new Hashtable();
        m_cms.getRegistry().getModuleExportables(moduleExportables);
        // now get the subIds of each module
        Hashtable availableModules = new Hashtable();
        Enumeration modElements = moduleExportables.elements();
        while (modElements.hasMoreElements()) {
            String classname = (String)modElements.nextElement();
            // get the subId of the module
            try {
                int subId = getContentDefinition(classname, new Class[] {CmsObject.class }, new Object[] {m_cms }).getSubId();
                // put the subid and the classname into the hashtable of available modules
                availableModules.put("" + subId, classname);
            } catch (Exception e) {
                // do nothing
            }

        }
        // now get the moduledata for import
        List masterNodes;
        Element currentElement;
        String subid;

        try {
            // get all master-nodes
            masterNodes = m_docXml.selectNodes("//" + CmsExportModuledata.C_EXPORT_TAG_MASTER);
            int length = masterNodes.size();

            // walk through all files in manifest
            for (int i = 0; i < length; i++) {
                currentElement = (Element)masterNodes.get(i);
                // get the subid of the modulemaster
                subid = CmsImport.getChildElementTextValue(currentElement, CmsExportModuledata.C_EXPORT_TAG_MASTER_SUBID);
                // check if there exists a module with this subid
                String classname = (String)availableModules.get(subid);
                if ((classname != null) && !("".equals(classname.trim()))) {
                    // import the dataset, the channelrelation and the media
                    m_report.print(" ( " + (i + 1) + " / " + length + " ) ", I_CmsReport.C_FORMAT_NOTE);
                    importMaster(subid, classname, currentElement);
                }
            }
        } catch (Exception exc) {
            throw new CmsException(CmsException.C_UNKNOWN_EXCEPTION, exc);
        }
    }

    /**
     * Imports a single master.<p>
     * 
     * @param subId the subid of the module
     * @param classname the name of the module class
     * @param currentElement the current element of the xml file
     * @throws CmsException in case something goes wrong
     */
    private void importMaster(String subId, String classname, Element currentElement) throws CmsException {
        // print out some information to the report
        m_report.print(m_report.key("report.importing"), I_CmsReport.C_FORMAT_NOTE);

        CmsMasterDataSet newDataset = new CmsMasterDataSet();
        Vector channelRelations = new Vector();
        Vector masterMedia = new Vector();
        // try to get the dataset
        try {
            int subIdInt = Integer.parseInt(subId);
            newDataset = getMasterDataSet(subIdInt, currentElement);
        } catch (Exception e) {
            m_report.println(e);
            throw new CmsException("Cannot get dataset ", e);
        }
        m_report.print("'" + CmsEncoder.escapeHtml(newDataset.m_title) + "' (" + classname + ")");
        m_report.print(m_report.key("report.dots"));
        // try to get the channelrelations
        try {
            channelRelations = getMasterChannelRelation(currentElement);
        } catch (Exception e) {
            m_report.println(e);
            throw new CmsException("Cannot get channelrelations ", e);
        }
        // try to get the media
        try {
            masterMedia = getMasterMedia(currentElement);
        } catch (Exception e) {
            m_report.println(e);
            throw new CmsException("Cannot get media ", e);
        }
        // add the channels and media to the dataset
        newDataset.m_channelToAdd = channelRelations;
        newDataset.m_mediaToAdd = masterMedia;
        // create the new content definition
        CmsMasterContent newMaster = getContentDefinition(classname, new Class[] {CmsObject.class, CmsMasterDataSet.class }, new Object[] {m_cms, newDataset });
        try {
            CmsUUID userId = newMaster.getOwner();
            CmsUUID groupId = newMaster.getGroupId();
            // first insert the new master
            newMaster.importMaster();
            // now update the master because user and group might be changed
            newMaster.chown(m_cms, userId);
            newMaster.chgrp(m_cms, groupId);
        } catch (Exception e) {
            m_report.println(e);
            throw new CmsException("Cannot write master ", e);
        }
        m_report.println(m_report.key("report.ok"), I_CmsReport.C_FORMAT_OK);
    }

    /**
     * Gets the dataset for the master from the xml file.<p>
     * 
     * @param subId the subid of the module
     * @param currentElement the current element of the xml file
     * @return the dataset with the imported information
     * @throws CmsException in case something goes wrong
     */
    private CmsMasterDataSet getMasterDataSet(int subId, Element currentElement) throws CmsException {
        String datasetfile, username, groupname, accessFlags, publicationDate, purgeDate, flags, feedId, feedReference, feedFilename, title;
        // get the new dataset object
        CmsMasterDataSet newDataset = new CmsMasterDataSet();

        // get the file with the dataset of the master
        datasetfile = CmsImport.getChildElementTextValue(currentElement, CmsExportModuledata.C_EXPORT_TAG_MASTER_DATASET);
        Document datasetXml = CmsImport.getXmlDocument(getFileReader(datasetfile));
        Element dataset = (Element) datasetXml.selectNodes("//" + CmsExportModuledata.C_EXPORT_TAG_MASTER_DATASET).get(0);
        // get the information from the dataset and add it to the dataset
        // first add the subid
        newDataset.m_subId = subId;
        newDataset.m_masterId = CmsUUID.getNullUUID();
        // get the id of the user or set the owner to the current user
        username = CmsImport.getChildElementTextValue(dataset, CmsExportModuledata.C_EXPORT_TAG_MASTER_USER);
        CmsUUID userId = m_cms.getRequestContext().currentUser().getId();
        try {
            if ((username != null) && !("".equals(username.trim()))) {
                userId = m_cms.readUser(username).getId();
            }
        } catch (Exception e) {
            // userId will be current user
        }
        newDataset.m_userId = userId;
        // get the id of the group or set the group to the current user        
        groupname = CmsImport.getChildElementTextValue(dataset, CmsExportModuledata.C_EXPORT_TAG_MASTER_GROUP);

        CmsUUID groupId = CmsUUID.getNullUUID();
        try {
            if ((groupname != null) && !("".equals(groupname.trim()))) {
                groupId = m_cms.readGroup(groupname).getId();
            }
        } catch (Exception e) {
            try {
                groupId = m_cms.readGroup(OpenCms.getDefaultUsers().getGroupUsers()).getId();
            } catch (Exception e2) {
                // ignore
            }
        }

        newDataset.m_groupId = groupId;
        // set the accessflags or the default flags
        accessFlags = CmsImport.getChildElementTextValue(dataset, CmsExportModuledata.C_EXPORT_TAG_MASTER_ACCESSFLAGS);
        try {
            newDataset.m_accessFlags = Integer.parseInt(accessFlags);
        } catch (Exception e) {
            newDataset.m_accessFlags = I_CmsConstants.C_ACCESS_DEFAULT_FLAGS;
        }
        // set the publication date
        publicationDate = CmsImport.getChildElementTextValue(dataset, CmsExportModuledata.C_EXPORT_TAG_MASTER_PUBLICATIONDATE);
        try {
            newDataset.m_publicationDate = convertDate(publicationDate);
        } catch (Exception e) {
            // ignore
        }
        // set the purge date
        purgeDate = CmsImport.getChildElementTextValue(dataset, CmsExportModuledata.C_EXPORT_TAG_MASTER_PURGEDATE);
        try {
            newDataset.m_purgeDate = convertDate(purgeDate);
        } catch (Exception e) {
            // ignore
        }
        // set the flags
        flags = CmsImport.getChildElementTextValue(dataset, CmsExportModuledata.C_EXPORT_TAG_MASTER_FLAGS);
        try {
            newDataset.m_flags = Integer.parseInt(flags);
        } catch (Exception e) {
            // ignore
        }
        // set the feedid
        feedId = CmsImport.getChildElementTextValue(dataset, CmsExportModuledata.C_EXPORT_TAG_MASTER_FEEDID);
        try {
            newDataset.m_feedId = Integer.parseInt(feedId);
        } catch (Exception e) {
            // ignore
        }
        // set the feedreference
        feedReference = CmsImport.getChildElementTextValue(dataset, CmsExportModuledata.C_EXPORT_TAG_MASTER_FEEDREFERENCE);
        try {
            newDataset.m_feedReference = Integer.parseInt(feedReference);
        } catch (Exception e) {
            // ignore
        }
        // set the feedfilenam
        feedFilename = CmsImport.getChildElementTextValue(dataset, CmsExportModuledata.C_EXPORT_TAG_MASTER_FEEDFILENAME);
        newDataset.m_feedFilename = feedFilename;
        // set the masters title
        title = CmsImport.getChildElementTextValue(dataset, CmsExportModuledata.C_EXPORT_TAG_MASTER_TITLE);
        newDataset.m_title = title;
        // set the values of data_big
        for (int i = 0; i < newDataset.m_dataBig.length; i++) {
            String filename = CmsImport.getChildElementTextValue(dataset, CmsExportModuledata.C_EXPORT_TAG_MASTER_DATABIG + i);
            String value = new String();
            if (filename != null && !"".equals(filename.trim())) {
                // get the value from the file
                value = new String(getFileBytes(filename));
            }
            newDataset.m_dataBig[i] = value;
        }
        // get the values of data_medium
        for (int i = 0; i < newDataset.m_dataMedium.length; i++) {
            String filename = CmsImport.getChildElementTextValue(dataset, CmsExportModuledata.C_EXPORT_TAG_MASTER_DATAMEDIUM + i);
            String value = new String();
            if (filename != null && !"".equals(filename.trim())) {
                // get the value from the file
                value = new String(getFileBytes(filename));
            }
            newDataset.m_dataMedium[i] = value;
        }
        // get the values of data_small
        for (int i = 0; i < newDataset.m_dataSmall.length; i++) {
            String filename = CmsImport.getChildElementTextValue(dataset, CmsExportModuledata.C_EXPORT_TAG_MASTER_DATASMALL + i);
            String value = new String();
            if (filename != null && !"".equals(filename.trim())) {
                // get the value from the file
                value = new String(getFileBytes(filename));
            }
            newDataset.m_dataSmall[i] = value;
        }
        // get the values of data_int
        for (int i = 0; i < newDataset.m_dataInt.length; i++) {
            String value = CmsImport.getChildElementTextValue(dataset, CmsExportModuledata.C_EXPORT_TAG_MASTER_DATAINT + i);
            try {
                newDataset.m_dataInt[i] = new Integer(value).intValue();
            } catch (Exception e) {
                newDataset.m_dataInt[i] = 0;
            }
        }
        // get the values of data_reference
        for (int i = 0; i < newDataset.m_dataReference.length; i++) {
            String value = CmsImport.getChildElementTextValue(dataset, CmsExportModuledata.C_EXPORT_TAG_MASTER_DATAREFERENCE + i);
            try {
                newDataset.m_dataReference[i] = new Integer(value).intValue();
            } catch (Exception e) {
                newDataset.m_dataReference[i] = 0;
            }
        }
        // get the values of data_date
        for (int i = 0; i < newDataset.m_dataDate.length; i++) {
            String value = CmsImport.getChildElementTextValue(dataset, CmsExportModuledata.C_EXPORT_TAG_MASTER_DATADATE + i);
            try {
                newDataset.m_dataDate[i] = convertDate(value);
            } catch (Exception e) {
                newDataset.m_dataDate[i] = 0;
            }
        }
        return newDataset;
    }

    /**
     * Gets the channel relations for the master from the xml file.<p>
     * 
     * @param currentElement the current element of the xml file
     * @return vector containing the ids of all channels of the master
     */
    private Vector getMasterChannelRelation(Element currentElement) {
        Vector channelRelations = new Vector();
        // get the channelnames of the master
        List channelNodes = currentElement.selectNodes("*/" + CmsExportModuledata.C_EXPORT_TAG_MASTER_CHANNELNAME);

        // walk through all channelrelations
        for (int j = 0; j < channelNodes.size(); j++) {
            // get the name of the channel
            String channelName = ((Element) channelNodes.get(j)).getTextTrim();
            // try to read the channel and get its channelid
            if ((channelName != null) && !("".equals(channelName.trim()))) {
                channelRelations.addElement(channelName);
            }
        }
        return channelRelations;
    }

    /**
     * Gets the media of the master from the xml file.<p>
     * 
     * @param currentElement The current element of the xml file
     * @return vector containing the media (CmsMasterMedia object) of the master
     * @throws CmsException in case something goes wrong
     */
    private Vector getMasterMedia(Element currentElement) throws CmsException {
        Vector masterMedia = new Vector();
        // get the mediafiles of the master
        List mediaNodes = currentElement.selectNodes("*/" + CmsExportModuledata.C_EXPORT_TAG_MASTER_MEDIA);
        // walk through all media
        for (int j = 0; j < mediaNodes.size(); j++) {
            // get the name of the file where the mediadata is stored
            String mediaFilename = ((Element) mediaNodes.get(j)).getTextTrim();
            // try to get the information of the media
            if ((mediaFilename != null) && !("".equals(mediaFilename.trim()))) {
                CmsMasterMedia newMedia = getMediaData(mediaFilename);
                masterMedia.add(newMedia);
            }
        }
        return masterMedia;
    }

    /**
     * Gets the information for a single media from the media file.<p>
     * 
     * @param mediaFilename the name of the xml file that contains the media information
     * @return the media information from the media file
     * @throws CmsException in case something goes wrong
     */
    private CmsMasterMedia getMediaData(String mediaFilename) throws CmsException {
        String position = null, width = null, height = null, size = null, mimetype = null, 
            type = null, title = null, name = null, description = null, contentfile = null;
        CmsMasterMedia newMedia = null;
        Document mediaXml = null;
        Element rootElement = null;
        byte[] mediacontent = null;
        
        newMedia = new CmsMasterMedia();
        mediaXml = CmsImport.getXmlDocument(getFileReader(mediaFilename));
        rootElement = mediaXml.getRootElement();
        
        position = ((Element)rootElement.selectNodes("./media/" + CmsExportModuledata.C_EXPORT_TAG_MEDIA_POSITION).get(0)).getTextTrim();        
        try {
            newMedia.setPosition(Integer.parseInt(position));
        } catch (Exception e) {
            // ignore
        }
        
        width = ((Element)rootElement.selectNodes("./media/" + CmsExportModuledata.C_EXPORT_TAG_MEDIA_WIDTH).get(0)).getTextTrim();
        try {
            newMedia.setWidth(Integer.parseInt(width));
        } catch (Exception e) {
            // ignore
        }
        
        height = ((Element)rootElement.selectNodes("./media/" + CmsExportModuledata.C_EXPORT_TAG_MEDIA_HEIGHT).get(0)).getTextTrim();
        try {
            newMedia.setHeight(Integer.parseInt(height));
        } catch (Exception e) {
            // ignore
        }
        
        size = ((Element)rootElement.selectNodes("./media/" + CmsExportModuledata.C_EXPORT_TAG_MEDIA_SIZE).get(0)).getTextTrim();
        try {
            newMedia.setSize(Integer.parseInt(size));
        } catch (Exception e) {
            // ignore
        }
        
        mimetype = ((Element)rootElement.selectNodes("./media/" + CmsExportModuledata.C_EXPORT_TAG_MEDIA_MIMETYPE).get(0)).getTextTrim();
        newMedia.setMimetype(mimetype);
        
        type = ((Element)rootElement.selectNodes("./media/" + CmsExportModuledata.C_EXPORT_TAG_MEDIA_TYPE).get(0)).getTextTrim();
        try {
            newMedia.setType(Integer.parseInt(type));
        } catch (Exception e) {
            // ignore
        }
        
        title = ((Element)rootElement.selectNodes("./media/" + CmsExportModuledata.C_EXPORT_TAG_MEDIA_TITLE).get(0)).getTextTrim();
        newMedia.setTitle(title);
        
        name = ((Element)rootElement.selectNodes("./media/" + CmsExportModuledata.C_EXPORT_TAG_MEDIA_NAME).get(0)).getTextTrim();
        newMedia.setName(name);
        
        description = ((Element)rootElement.selectNodes("./media/" + CmsExportModuledata.C_EXPORT_TAG_MEDIA_DESCRIPTION).get(0)).getTextTrim();
        newMedia.setDescription(description);
        
        contentfile = ((Element)rootElement.selectNodes("./media/" + CmsExportModuledata.C_EXPORT_TAG_MEDIA_CONTENT).get(0)).getTextTrim();
        try {
            mediacontent = getFileBytes(contentfile);
        } catch (Exception e) {
            m_report.println(e);
        }       
        newMedia.setMedia(mediacontent);
        
        return newMedia;
    }

    /**
     * Returns a buffered reader for this resource using the importFile as root.<p>
     *
     * @param filename the name of the file to read
     * @return the file reader for this file
     * @throws CmsException in case something goes wrong
     */
    private BufferedReader getFileReader(String filename) throws CmsException {
        try {
            // is this a zip-file?
            if (m_importZip != null) {
                // yes
                ZipEntry entry = m_importZip.getEntry(filename);
                InputStream stream = m_importZip.getInputStream(entry);
                return new BufferedReader(new InputStreamReader(stream));
            } else {
                // no - use directory
                File xmlFile = new File(m_importResource, filename);
                return new BufferedReader(new FileReader(xmlFile));
            }
        } catch (Exception e) {
            throw new CmsException(CmsException.C_UNKNOWN_EXCEPTION, e);
        }
    }

    /**
     * Coverts a String Date in long.<p>
     *
     * @param date String
     * @return long converted date
     */
    private long convertDate(String date) {
        java.text.SimpleDateFormat formatterFullTime = new SimpleDateFormat("dd.MM.yyyy HH:mm");
        long adate = 0;
        try {
            adate = formatterFullTime.parse(date).getTime();
        } catch (ParseException e) {
            // ignore
        }
        return adate;
    }


    /**
     * Gets the content definition class method constructor.<p>
     * 
     * @param classname the name of the cd class
     * @param classes types needed for cd constructor
     * @param objects objects needed for cd constructor
     * @return content definition object
     */
    protected CmsMasterContent getContentDefinition(String classname, Class[] classes, Object[] objects) {
        CmsMasterContent cd = null;
        try {
            Class cdClass = Class.forName(classname);
            Constructor co = cdClass.getConstructor(classes);
            cd = (CmsMasterContent)co.newInstance(objects);
        } catch (InvocationTargetException ite) {
            if (OpenCms.getLog(this).isWarnEnabled()) {
                OpenCms.getLog(this).warn("Invocation target exception", ite);
            }
        } catch (NoSuchMethodException nsm) {
            if (OpenCms.getLog(this).isWarnEnabled()) {
                OpenCms.getLog(this).warn("Requested method was not found", nsm);
            }
        } catch (InstantiationException ie) {
            if (OpenCms.getLog(this).isWarnEnabled()) {
                OpenCms.getLog(this).warn("The reflected class is abstract", ie);
            }
        } catch (Exception e) {
            if (OpenCms.getLog(this).isWarnEnabled()) {
                OpenCms.getLog(this).warn("Other exception", e);
            }
        }
        return cd;
    }
}
