package com.groxicTinch.imgsorter;

import java.awt.event.*;
import java.awt.Graphics;
import java.awt.Image;
import java.io.File;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.util.*; 
import java.nio.file.Files;
import java.nio.file.Paths;

import javax.imageio.ImageIO;

import javax.swing.AbstractAction;
import javax.swing.JFileChooser;
import javax.swing.KeyStroke;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.SwingConstants;

import javax.swing.JOptionPane;

/*
  How To!
  Press File and choose a folder that has images, for example ./stable-diffusion-webui/outputs
  ImgSorter will look inside all subdirectories(so dont select any folder with too many images!)
  for images and display the first, when you choose an option the image will move to either
  ./SortedImages/Keep, ./SortedImages/Maybe or ./SortedImages/Exclude
  To send the current image to:
  Keep, press 1 or numpad1,
  Maybe, press 2 or numpad2,
  Exclude press 3 or numpad3

  If you make a mistake and want to go back, press 4 or numpad4

  The program will remember the last selected project folder but dont select a folder
 */
public class ImgSorter extends JFrame implements ActionListener {
  public final static class EventTriggered {
    public static final int KEEPIMAGE = 0;
    public static final int MAYBEIMAGE = 1;
    public static final int EXCLUDEIMAGE = 2;
    public static final int GOBACK = 3;
  }

  public static String mainFolder = "./SortedImages/";

  public static String keepFolder = mainFolder + "Keep/";
  public static String maybeFolder = mainFolder + "Maybe/";
  public static String excludeFolder = mainFolder + "Exclude/";

  public static String openedFolder;

  private JLabel label;
  private JMenuItem openItem;
  private JMenuItem exitItem;

  private JButton btnKeep;
  private JButton btnMaybe;
  private JButton btnExclude;
  private JButton btnBack;

  public static ArrayList<ImgObj> imgList;
  public static int curImgIndex;

  File configFile = new File("lastOpenedFolder.txt");

  Image image;

  // No clue what this part does, credit to HenryLoenwind at https://github.com/HenryLoenwind/sdcompare whose code I had to borrow parts from to get the hotkeys working
  private static final AbstractAction CLICK_ACTION = new AbstractAction() {
    @Override
    public void actionPerformed(ActionEvent e) {
      if (e.getSource() instanceof JButton) {
        ((JButton) e.getSource()).doClick();
        ((JButton) e.getSource()).requestFocusInWindow();
      }
    }
  };

  public ImgSorter() {
    setTitle("ImgSorter");
    setSize(512, 558);
    setLocationRelativeTo(null);

    JMenuBar mbar = new JMenuBar();
    JMenu m = new JMenu("File");
    openItem = new JMenuItem("Open");
    openItem.addActionListener(this);
    m.add(openItem);
    exitItem = new JMenuItem("Exit");
    exitItem.addActionListener(this);
    m.add(exitItem);
    mbar.add(m);
    setJMenuBar(mbar);

    btnKeep = new JButton("Keep");
    btnMaybe = new JButton("Maybe");
    btnExclude = new JButton("Exclude");
    btnBack = new JButton("Back");

    btnKeep.addActionListener(e -> processImage(EventTriggered.KEEPIMAGE));
    btnMaybe.addActionListener(e -> processImage(EventTriggered.MAYBEIMAGE));
    btnExclude.addActionListener(e -> processImage(EventTriggered.EXCLUDEIMAGE));
    btnBack.addActionListener(e -> processImage(EventTriggered.GOBACK));

    setupHotkeys(btnKeep, KeyEvent.VK_1, KeyEvent.VK_NUMPAD1);
    setupHotkeys(btnMaybe, KeyEvent.VK_2, KeyEvent.VK_NUMPAD2);
    setupHotkeys(btnExclude, KeyEvent.VK_3, KeyEvent.VK_NUMPAD3);
    setupHotkeys(btnBack, KeyEvent.VK_4, KeyEvent.VK_NUMPAD4);

    label = new JLabel("No Loaded Images", SwingConstants.CENTER) {
      public void paintComponent(Graphics g)
      {
        super.paintComponent(g);
        if(image != null) {
          float rateX =  (float)getWidth()/(float)image.getWidth(null);
          float rateY = (float)getHeight()/(float)image.getHeight(null);
          if (rateX>rateY){
            int W=(int)(image.getWidth(null)*rateY);
            int H=(int)(image.getHeight(null)*rateY);
            g.drawImage(image, 0, 0,W,H, null);
          }
          else{
            int W=(int)(image.getWidth(null)*rateX);
            int H=(int)(image.getHeight(null)*rateX);
            g.drawImage(image, 0, 0,W,H, null);
          }
        }
      }
    };
    
    add(btnKeep);
    add(btnMaybe);
    add(btnExclude);
    add(btnBack);
    add(label);

    setVisible(true);

    addWindowListener(new WindowAdapter()
    {
       public void windowClosing(WindowEvent e)
       {
         dispose();
         System.exit(0); //calling the method is a must
       }
    });

    if(configFile.isFile()) {
      try {
        Scanner myReader = new Scanner(configFile);
        while (myReader.hasNextLine()) {
          File f = new File(myReader.nextLine());
          if(f.isDirectory()) {
            setFolder(f);
          }
        }
        myReader.close();
      } catch (FileNotFoundException e) {
      }
    }
  }

  private void processImage(int choice) {
    if(imgList == null)
      return;

    switch(choice) {
      case EventTriggered.KEEPIMAGE:
        imgList.get(curImgIndex).moveToKeep();
        break;
      case EventTriggered.MAYBEIMAGE:
        imgList.get(curImgIndex).moveToMaybe();
        break;
      case EventTriggered.EXCLUDEIMAGE:
        imgList.get(curImgIndex).moveToExcluded();
        break;
      default:
        //EventTriggered.GOBACK
        curImgIndex -= 2;
    }

    curImgIndex++;

    if(curImgIndex < 0) {
      curImgIndex = 0;
      JOptionPane.showMessageDialog(null, "This was the first image");
    } else if(curImgIndex >= imgList.size()) {
      curImgIndex = imgList.size()-1;
      JOptionPane.showMessageDialog(null, "This was the last image");
    } else {
      showImage(imgList.get(curImgIndex));
    }
  }

  public void actionPerformed(ActionEvent evt) {
    Object source = evt.getSource();
    if (source == openItem) {
      JFileChooser chooser = new JFileChooser();
      chooser.setCurrentDirectory(new File("../"));
      chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
      chooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
        @Override
        public boolean accept(File f) {
          return f.isDirectory();
        }

        @Override
        public String getDescription() {
          return "Folder containing images(will recurse)";
        }
      });

      int r = chooser.showOpenDialog(this);
      if (r == JFileChooser.APPROVE_OPTION) {
        setFolder(chooser.getSelectedFile());
      }
    } else if (source == exitItem)
      System.exit(0);
  }

  public void setFolder(File file) {
    curImgIndex = 0;
    imgList = new ArrayList<ImgObj>();

    if(file != null) {
       try {
        FileWriter myWriter = new FileWriter(configFile.getAbsolutePath());
        myWriter.write(file.getAbsolutePath());
        myWriter.close();

        openedFolder = file.getAbsolutePath();
      } catch (IOException e) {
        System.out.println("An error occurred.");
        e.printStackTrace();
      }

      FilesToImgObj(file.listFiles());
      if(imgList.size() > 0)
        showImage(imgList.get(curImgIndex));
    }
  }

  public void showImage(ImgObj imgObj) {
    if (imgObj == null) {
      label.setText("No Image Loaded");
      return;
    }

    try {
      File file = imgObj.getFile();
      setTitle("ImgSorter - Showing image " + (curImgIndex + 1) + "/" + imgList.size() + " " + file.getAbsolutePath());
      image = ImageIO.read(file);
      repaint();
    } catch (IOException e) {
      curImgIndex = 0;
      e.printStackTrace();
    }
  }

  public static void FilesToImgObj(File[] files) {
      for (File file : files) {
          if (file.isDirectory()) {
              FilesToImgObj(file.listFiles()); // Calls same method again.
          } else {
            String name = file.getName();
            if(name.endsWith(".png") || name.endsWith(".jpg"))
              imgList.add(new ImgObj(file));
          }
      }
  }

  // This code is also by HenryLoenwind
  private static void setupHotkeys(JButton button, int... keys) {
    for (int key : keys) {
      button.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key, 0), "click");
    }
    button.getActionMap().put("click", CLICK_ACTION);
  }
  // **********************************

  public static void main(String[] args) {
    JFrame frame = new ImgSorter();
    frame.show();
  }

  public static class ImgObj {
    File file;
    String relativePathFromOpened;

    public ImgObj(File fileIn) {
      file = fileIn;
    }

    public File getFile() {
      return file;
    }

    public String getName() {
      return file.getName();
    }

    public void moveToKeep() {
      moveTo(keepFolder);
    }

    public void moveToMaybe() {
      moveTo(maybeFolder);
    }

    public void moveToExcluded() {
      moveTo(excludeFolder);
    }

    void moveTo(String newFolder) {
      try {
        if(relativePathFromOpened == null) {
          relativePathFromOpened = file.getAbsolutePath().replace(openedFolder, "");
        }

        File movedFile = new File(newFolder + relativePathFromOpened);
        Files.createDirectories(Paths.get(movedFile.getAbsolutePath()).getParent());
        file.renameTo(movedFile);
        file = movedFile;
      } catch(IOException e) {
        e.printStackTrace();
      }
    }
  }
}