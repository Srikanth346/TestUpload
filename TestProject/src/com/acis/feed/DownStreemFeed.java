package com.acis.feed;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;
import com.acis.feed.Config.Enum_Config;
import com.acis.feed.Config;
import com.acis.feed.FoldersCreation.Enum_Feeds;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

public class DownStreemFeed {

	private static String hostServer = new Config().getConfigPropValue(Enum_Config.UNIXHostServer);
	private static Session serverSession = null;
	public static Config config = null;

	/**
	 * Function Name : verifyFolderPath Description : This function is used to
	 * get Connection to SSHD Server
	 **/
	protected static Session getConnection(String hostServer, String userName, String password) throws JSchException {
		// Get Properties
		java.util.Properties config = new java.util.Properties();
		config.put("StrictHostKeyChecking", "no");
		JSch javasch = new JSch();
		Session sftpsession = null;
		try {
			sftpsession = javasch.getSession(userName, hostServer, 22);
			sftpsession.setPassword(password);
			sftpsession.setConfig(config);
			sftpsession.connect();
			if (sftpsession.isConnected()) {
				System.out.println("Connected to Server " + sftpsession.getHost().toString());
			} else {
				System.out.println("Connected establishemnt failed to Server " + hostServer);
			}
		} catch (JSchException jschException) {
			System.out.println("Unable to Connect to Server ");
		}
		return sftpsession;
	}

	/**
	 * Function Name : verifyFolderPath Description : This function is used to
	 * pretty Print XML Document
	 **/
	private static Document prettyPrintXML(String filePath) {
		Document document = null;
		try {
			DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
			document = docBuilder.parse(new java.io.File(filePath));
			Transformer tform = TransformerFactory.newInstance().newTransformer();
			tform.setOutputProperty(OutputKeys.INDENT, "yes");
			tform.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
			tform.transform(new DOMSource(document), new StreamResult(new java.io.File(filePath)));
		} catch (ParserConfigurationException | SAXException | IOException | TransformerFactoryConfigurationError
				| TransformerException exception) {
			exception.printStackTrace();
		}
		return document;
	}

	/**
	 * Function Name : transferFileFromServer Description : This function is
	 * used to transfer a File from SFTP Server
	 **/
	public static void transferFileFromServer(Session session, String latestFile, Enum_Feeds feedType) {
		ChannelSftp channel = null;
		String outputFilePath = "";
		String serverFilePath = "//acis/acisat7/ftp/" + feedType.toString() + "/" + latestFile;
		System.out.println("Server path " + serverFilePath );
		try {
			// Create a Channel to SFTP
			channel = (ChannelSftp) session.openChannel("sftp");
			// Connected to SFTP Channel
			channel.connect();
			if (channel.isConnected()) {
				System.out.println("Connected to Sftp Channel");
			} else {
				System.out.println("Not Connected to Sftp Channel");
			}
			//Get OutputfilePath  
			switch(feedType.getFolderName()){
			case "alliance":
				outputFilePath = FoldersCreation.getOutputFolderPath(Enum_Feeds.UP2S) + latestFile ;
				break;
			case "rxsolutions":
				outputFilePath = FoldersCreation.getOutputFolderPath(Enum_Feeds.RXSolution) + latestFile;
				break;
			case "ecap":
				outputFilePath =  FoldersCreation.getOutputFolderPath(Enum_Feeds.ECAP) + latestFile;
				break;
			case "aup":
				outputFilePath = FoldersCreation.getOutputFolderPath(Enum_Feeds.AUP) + latestFile;
				break;
			}
			System.out.println(outputFilePath);
			InputStream out = channel.get(serverFilePath.toString());
			BufferedReader br = new BufferedReader(new InputStreamReader(out));
			BufferedWriter bw = new BufferedWriter(new FileWriter(outputFilePath));
			System.out.println(" Reading File");
			String line;
			while ((line = br.readLine()) != null) {
				bw.write(line);
			}
			// Close Buffered Writer
			bw.close();
			// Close Buffered Reader
			br.close();
			// Close Channel
			channel.disconnect();
			// Pretty Print XML Document
			prettyPrintXML(outputFilePath);
			System.out.println("Pretty Print of XML Done.");
			System.out.println(
					"**************************************************************************************************************");
			System.out.println("File Saved." + outputFilePath);
			System.out.println(
					"**************************************************************************************************************");
		} catch (JSchException jschException) {
			System.out.println("Unable to create SFTP Channel to Host : " + hostServer);
			System.out.println("File Tranfer from Host :" + hostServer + " got Failed");
			jschException.printStackTrace();
		} catch (Exception exception) {
			if( exception instanceof IOException  ) System.out.println(exception.getMessage()); 
			exception.printStackTrace();
		} finally {
			if (channel.isClosed())
				channel.disconnect();
		}
	}

	/**
	 * Function Name : executeCommand Description : This function is used to
	 * execute command's on connected host
	 **/
	public static String executeCommand(Session cmdSession, String command) {
		StringBuilder outputBuffer = new StringBuilder();
		Channel channel = null;
		try {
			channel = cmdSession.openChannel("exec");
			((ChannelExec) channel).setCommand(command);
			InputStream commandOutput = channel.getInputStream();
			channel.connect();
			int readByte = commandOutput.read();
			// Read command output
			while (readByte != 0xffffffff) {
				outputBuffer.append((char) readByte);
				readByte = commandOutput.read();
			}
			channel.disconnect();
		} catch (Exception exception) {
			if (exception instanceof IOException) {
				System.out.println("Unable to Execute Command " + command);
			}
			if (exception instanceof JSchException) {
				System.out.println("Unable to Execute Command " + command);
			}
			return null;
		} finally {
			if (!channel.isClosed())
				channel.disconnect();
		}
		return outputBuffer.toString();
	}

	public static void main(String[] args) throws JSchException {
		String latestFile = "";
		config = new Config();
		String userName = config.getConfigPropValue(Enum_Config.UserName);
		String log;
		String tranId = "560219";
		try {
			serverSession = DownStreemFeed.getConnection( hostServer, userName, "TUXup@1T" );
			
			for(int i=0;i<30;i++) { System.out.print("---"); }
			System.out.println("");
			//UP2S 
			log = executeCommand(serverSession, "/acis/acisat7/bin/RELaunch_S.sh acisat7 " + tranId
					+ " Alliance.xml;/acisweb/bin/Collector.sh acisat7 Alliance-collector.xml");
			System.out.println(log);
			latestFile = executeCommand(serverSession, "cd /acis/acisat7/ftp/alliance/;ls -t1 --file-type *.xml |  head -n 1");
			System.out.println("Latest File: " +  latestFile );
			transferFileFromServer(serverSession, latestFile , Enum_Feeds.UP2S);
			
			/*for(int i=0;i<30;i++) { System.out.print("---"); }
			System.out.println("");
			//RxSolution  
			log = executeCommand(serverSession, "/acis/acisat7/bin/RELaunch_S.sh acisat7 " + tranId
					+ " cisxml2.0.xml:RxSolutions;/acisweb/bin/RxSolutions_v2.sh acisat7 Daily TODAY");
			System.out.println(log);
			latestFile = executeCommand(serverSession, "cd /acis/acisat7/ftp/rxsolutions/;ls -t1 --file-type *.xml |  head -n 1");
			System.out.println("Latest File: " +  latestFile );
			transferFileFromServer(serverSession, latestFile , Enum_Feeds.RXSolution);*/
			
			//Disconnect from Unix Server 
			serverSession.disconnect();
			// Push File to Git Hub in Remote
			JGit.pullChangesFromGit();
			JGit.pushChangesToGit();
			System.out.println("File Pushed to Git");
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (!serverSession.isConnected())
				serverSession.disconnect();
		}
	}
}