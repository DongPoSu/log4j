/*
 * Created on Sep 8, 2003
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package org.apache.log4j.chainsaw;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Vector;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;

import org.apache.log4j.Layout;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.chainsaw.LogUI.TabIconHandler;
import org.apache.log4j.chainsaw.icons.ChainsawIcons;
import org.apache.log4j.chainsaw.layout.DefaultLayoutFactory;
import org.apache.log4j.chainsaw.layout.EventDetailLayout;
import org.apache.log4j.chainsaw.layout.LayoutEditorPane;
import org.apache.log4j.chainsaw.prefs.LoadSettingsEvent;
import org.apache.log4j.chainsaw.prefs.SaveSettingsEvent;
import org.apache.log4j.chainsaw.prefs.SettingsListener;
import org.apache.log4j.chainsaw.prefs.SettingsManager;
import org.apache.log4j.chainsaw.rule.AbstractRule;
import org.apache.log4j.helpers.LogLog;
import org.apache.log4j.spi.LocationInfo;
import org.apache.log4j.spi.LoggingEvent;


/**
   * LogPanel encapsulates all the necessary bits and pieces of a
   * floating window of Events coming from a specific Location.
   *
   * This is where most of the Swing components are constructed and laid out.
   */
  public class LogPanel extends DockablePanel implements SettingsListener,
    EventBatchListener {
    private final LogUI logUI;
	private boolean paused = false;
    final ColorFilter colorFilter = new ColorFilter();
    private final RuleMediator ruleMediator = new RuleMediator();
    final EventContainer tableModel;
    final JEditorPane detail;
    final JSplitPane lowerPanel;
    final DetailPaneUpdater detailPaneUpdater;
    final JPanel upperPanel;
    final JPanel eventsAndStatusPanel;
    final JFrame f;
    final DockablePanel externalPanel;
    final Action dockingAction;
    final JSortTable table;
    private String profileName = null;
    boolean isDocked = true;
    String identifier;
    final Map columnDisplayMap = new HashMap();
    final Map colorDisplayMap = new HashMap();
    final Set loggerSet = new HashSet();
//    final ColorDisplaySelector colorDisplaySelector;
    Set MDCSet = new HashSet();
    Set NDCSet = new HashSet();
    Set threadSet = new HashSet();
    Set classSet = new HashSet();
    Set methodSet = new HashSet();
    Set fileSet = new HashSet();
    Set levelSet = new HashSet();
    ScrollToBottom scrollToBottom;
    private final LogPanelLoggerTreeModel logTreeModel =
      new LogPanelLoggerTreeModel();
    private Layout detailPaneLayout = new EventDetailLayout();
    private Layout toolTipLayout = new EventDetailLayout();

    //used for consistency - stays empty - used to allow none set in the colordisplay selector and right click
    Set noneSet = new HashSet();
    Point currentPoint;
    private final JSplitPane nameTreeAndMainPanelSplit;
    private final LoggerNameTreePanel logTreePanel;
	private boolean tooltipsEnabled;

    public LogPanel(final LogUI logUI, final String ident, String eventType) {
      identifier = ident;
	this.logUI = logUI;

      setDetailPaneConversionPattern(
        DefaultLayoutFactory.getDefaultPatternLayout());
      ((EventDetailLayout) toolTipLayout).setConversionPattern(
        DefaultLayoutFactory.getDefaultPatternLayout());

      Map map = new HashMap();
      logUI.getEntryMap().put(ident, map);

      int bufferSize = 500;

      //if buffer size not provided, set default buffer size to 500 (only used if usecyclicbuffer true)
      if (System.getProperty(LogUI.CYCLIC_BUFFER_SIZE_PROP_NAME) != null) {
        bufferSize =
          Integer.valueOf(System.getProperty(LogUI.CYCLIC_BUFFER_SIZE_PROP_NAME))
                 .intValue();
      }

      tableModel =
        new ChainsawCyclicBufferTableModel(
          Boolean.valueOf(System.getProperty(LogUI.USE_CYCLIC_BUFFER_PROP_NAME))
                 .booleanValue(), bufferSize);

      table = new JSortTable(tableModel);
      table.getColumnModel().addColumnModelListener(
        new ChainsawTableColumnModelListener(table));

      table.setAutoCreateColumnsFromModel(false);

      /**
               * We listen for new Key's coming in so we can get them automatically added as columns
              */
      tableModel.addNewKeyListener(
        new NewKeyListener() {
          public void newKeyAdded(NewKeyEvent e) {
            table.addColumn(new TableColumn(e.getNewModelIndex()));
          }
        });

      table.setRowHeight(20);
      table.setShowGrid(false);

      scrollToBottom = new ScrollToBottom(true);

      // ==========================================
      tableModel.addLoggerNameListener(logTreeModel);
      
      /**
       * Set the Display rule to use the mediator, the model will add itself as a property
       * change listener and update itself when the rule changes.
       */
      tableModel.setDisplayRule(ruleMediator);
      
      logTreePanel = new LoggerNameTreePanel(logTreeModel);

	/**
	 * We listen for when the FocusOn action changes, and then ensure the Refinement filter is set
	 * accordingly.
	 */
	  logTreePanel.addFocusOnPropertyChangeListener( new PropertyChangeListener(){

		public void propertyChange(PropertyChangeEvent evt) {
			if(logTreePanel.isFocusOnSelected()){
				final String loggerName = logTreePanel.getCurrentlySelectedLoggerName();
				ruleMediator.setRefinementRule(new AbstractRule(){

					public boolean evaluate(LoggingEvent e) {
						return e.getLoggerName().startsWith(loggerName);
					}});
			} else {
				ruleMediator.setRefinementRule(null);
			}
		}});
      levelSet = new HashSet((List) logUI.getLevelMap().get(eventType));

      map.put(ChainsawConstants.LEVEL_COL_NAME, levelSet);

      map.put(ChainsawConstants.LOGGER_COL_NAME, loggerSet);
      map.put(ChainsawConstants.THREAD_COL_NAME, threadSet);
      map.put(ChainsawConstants.NDC_COL_NAME, NDCSet);
      map.put(ChainsawConstants.MDC_COL_NAME, MDCSet);
      map.put(ChainsawConstants.CLASS_COL_NAME, classSet);
      map.put(ChainsawConstants.METHOD_COL_NAME, methodSet);
      map.put(ChainsawConstants.FILE_COL_NAME, fileSet);
      map.put(ChainsawConstants.NONE_COL_NAME, noneSet);

      setLayout(new BorderLayout());
      
//      TODO reload new Display rule for this panel
//      displayFilter = loadDisplayFilter(ident);
//      tableModel.setDisplayRule(displayFilter);
//      displayFilter.addFilterChangedListener(tableModel);

      logUI.getScrollMap().put(ident, scrollToBottom);

      TableColorizingRenderer renderer = new TableColorizingRenderer();
      logUI.getSettingsManager().addSettingsListener(renderer);
      logUI.getSettingsManager().configure(renderer);

      renderer.setColorFilter(colorFilter);

      table.setDefaultRenderer(Object.class, renderer);

      //if the color filter changes, trigger the tablemodel update
      colorFilter.addFilterChangedListener(
        new FilterChangedListener() {
          public void filterChanged() {
            if (tableModel instanceof AbstractTableModel) {
              ((AbstractTableModel) tableModel).fireTableDataChanged();
            }
          }
        });

      final ColumnSelector columnSelector =
        new ColumnSelector(
          ident, new Vector(ChainsawColumns.getColumnsNames()), table);
      table.getColumnModel().addColumnModelListener(columnSelector);
      
      columnSelector.setIconImage(logUI.getIconImage());

      JMenu menuColumnDisplayFilter =
        new JMenu("Apply display filter for column");

      JMenu menuColumnColorFilter = new JMenu("Apply color filter for column");

      ButtonGroup bg = new ButtonGroup();
      Iterator iter = logUI.getFilterableColumns().iterator();

      while (iter.hasNext()) {
        final String colName = (String) iter.next();
        JRadioButtonMenuItem thisItem = new JRadioButtonMenuItem(colName);
        thisItem.setFont(thisItem.getFont().deriveFont(Font.PLAIN));
        thisItem.addActionListener(
          new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
              LoggingEvent lastSelected = null;

              if (table.getSelectedRow() > -1) {
                lastSelected = tableModel.getRow(table.getSelectedRow());
              }

//              colorDisplaySelector.applyColorUpdateForColumn(colName);
//              colorDisplaySelector.applyColorFilters(colName);

              if (lastSelected != null) {
                int newIndex = tableModel.getRowIndex(lastSelected);

                if (newIndex > -1) {
                  table.scrollToRow(
                    newIndex,
                    table.columnAtPoint(table.getVisibleRect().getLocation()));
                }
              }
            }
          });
        bg.add(thisItem);
        menuColumnColorFilter.add(thisItem);
        colorDisplayMap.put(colName, thisItem);
      }

      ButtonGroup bg2 = new ButtonGroup();
      Iterator iter2 = logUI.getFilterableColumns().iterator();

      while (iter2.hasNext()) {
        final String colName = (String) iter2.next();
        JRadioButtonMenuItem thisItem = new JRadioButtonMenuItem(colName);
        thisItem.setFont(thisItem.getFont().deriveFont(Font.PLAIN));
        thisItem.addActionListener(
          new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
              LoggingEvent lastSelected = null;

              if (table.getSelectedRow() > -1) {
                lastSelected = tableModel.getRow(table.getSelectedRow());
              }

//              colorDisplaySelector.applyDisplayUpdateForColumn(colName);
//              colorDisplaySelector.applyDisplayFilters(colName);

              if (lastSelected != null) {
                int newIndex = tableModel.getRowIndex(lastSelected);

                if (newIndex > -1) {
                  table.scrollToRow(
                    newIndex,
                    table.columnAtPoint(table.getVisibleRect().getLocation()));
                }
              }
            }
          });
        bg2.add(thisItem);
        menuColumnDisplayFilter.add(thisItem);
        columnDisplayMap.put(colName, thisItem);
      }

      table.addMouseMotionListener(
        new MouseMotionAdapter() {
          int currentRow = -1;

          public void mouseMoved(MouseEvent evt) {
            currentPoint = evt.getPoint();

//			TODO refactor so that LogPanel itself knows if Tooltips are enabled
            if (false) {
              int row = table.rowAtPoint(evt.getPoint());

              if ((row == currentRow) || (row == -1)) {
                return;
              }

              currentRow = row;

              LoggingEvent event = tableModel.getRow(currentRow);
              Layout layout = getToolTipLayout();

              if (event != null) {
                StringBuffer buf = new StringBuffer();
                buf.append(layout.getHeader()).append(layout.format(event))
                   .append(layout.getFooter());
                table.setToolTipText(buf.toString());
              }
            } else {
              table.setToolTipText(null);
            }
          }
        });

      //HACK - fix the way columns are sized..should be saved off and loaded later
      table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

      detail = new JEditorPane(ChainsawConstants.DETAIL_CONTENT_TYPE, "");
      detail.setEditable(false);

      detailPaneUpdater =
        new DetailPaneUpdater(this, detail, (EventContainer) tableModel);

      upperPanel = new JPanel(new BorderLayout());
      upperPanel.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 0));

      final JLabel filterLabel = new JLabel("QuickFilter: ");
      filterLabel.setFont(filterLabel.getFont().deriveFont(Font.BOLD));

      JPanel upperLeftPanel =
        new JPanel(new FlowLayout(FlowLayout.CENTER, 3, 0));
      upperLeftPanel.add(filterLabel);

      final JComboBox customFilterList =
        new JComboBox(ChainsawColumns.getColumnsNames().toArray());
      customFilterList.setFont(customFilterList.getFont().deriveFont(10f));

      final JTextField filterText = new JTextField();

      filterText.getDocument().addDocumentListener(
        new DocumentListener() {
          public void insertUpdate(DocumentEvent e) {
            setFilter();
          }

          public void removeUpdate(DocumentEvent e) {
            setFilter();
          }

          public void changedUpdate(DocumentEvent e) {
            setFilter();
          }

          public void setFilter() {
          	LogLog.warn("setFilter() is not re-implemented in terms of the new Display rule yet");
//          	TODO Display rule stuff
            if (filterText.getText().equals("")) {
//              displayFilter.setCustomFilter(null);
            } else {
//              detailPaneUpdater.setSelectedRow(-1);
//
//              displayFilter.setCustomFilter(
//                new DisplayFilterEntry(
//                  (String) customFilterList.getSelectedItem(),
//                  filterText.getText(), ChainsawConstants.GLOBAL_MATCH));
            }
          }
        });

      String evaluator =
        ExpressionEvaluatorFactory.newInstance().getEvaluatorClassName();
      filterText.setToolTipText(
        "See " + evaluator + " documentation for expression rules");

      customFilterList.setMaximumRowCount(15);

      upperLeftPanel.add(customFilterList);

      customFilterList.addActionListener(
        new ActionListener() {
          public void actionPerformed(ActionEvent evt) {
            if (!(filterText.getText().equals(""))) {
            	
//            	TODO more Rule implementation here.
//              displayFilter.setCustomFilter(
//                new DisplayFilterEntry(
//                  (String) customFilterList.getSelectedItem(),
//                  filterText.getText(), ChainsawConstants.GLOBAL_MATCH));
            }
          }
        });

      upperPanel.add(filterText, BorderLayout.CENTER);
      upperPanel.add(upperLeftPanel, BorderLayout.WEST);

      final JCheckBox override = new JCheckBox();
      override.addActionListener(
        new ActionListener() {
          public void actionPerformed(ActionEvent evt) {
            LoggingEvent lastSelected = null;

            if (table.getSelectedRow() > -1) {
              lastSelected = tableModel.getRow(table.getSelectedRow());
            }

//            displayFilter.setCustomFilterOverride(override.isSelected());

            if (lastSelected != null) {
              int newIndex = tableModel.getRowIndex(lastSelected);

              if (newIndex > -1) {
                table.scrollToRow(
                  newIndex,
                  table.columnAtPoint(table.getVisibleRect().getLocation()));
              }
            }
          }
        });

      override.setToolTipText(
        "<html>Unchecked: Apply QuickFilter to displayed rows<br>Checked: Apply QuickFilter to ALL rows (override display filter setting)</html>");

      JPanel upperRightPanel =
        new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
      upperRightPanel.add(override);

      upperPanel.add(upperRightPanel, BorderLayout.EAST);

      eventsAndStatusPanel = new JPanel();
      eventsAndStatusPanel.setLayout(new BorderLayout());

      final JScrollPane eventsPane = new JScrollPane(table);

      eventsPane.setPreferredSize(new Dimension(900, 300));

      eventsAndStatusPanel.add(eventsPane, BorderLayout.CENTER);

      final JPanel statusLabelPanel = new JPanel();
      statusLabelPanel.setLayout(new BorderLayout());

      final JLabel statusPaneLabel = new JLabel();
      statusPaneLabel.setFont(statusPaneLabel.getFont().deriveFont(Font.BOLD));
      statusLabelPanel.setBorder(BorderFactory.createEtchedBorder());
      statusPaneLabel.setHorizontalAlignment(JLabel.LEFT);
      statusPaneLabel.setVerticalAlignment(JLabel.CENTER);
      statusLabelPanel.add(statusPaneLabel, BorderLayout.WEST);
      statusLabelPanel.add(upperPanel, BorderLayout.CENTER);
      eventsAndStatusPanel.add(statusLabelPanel, BorderLayout.NORTH);

      final JPanel detailPanel = new JPanel(new BorderLayout());

      //set valueisadjusting if holding down a key - don't process setdetail events
      table.addKeyListener(
        new KeyListener() {
          public void keyTyped(KeyEvent e) {
          }

          public void keyPressed(KeyEvent e) {
            synchronized (detail) {
              table.getSelectionModel().setValueIsAdjusting(true);
              detail.notify();
            }
          }

          public void keyReleased(KeyEvent e) {
            synchronized (detail) {
              table.getSelectionModel().setValueIsAdjusting(false);
              detail.notify();
            }
          }
        });

      final JScrollPane detailPane = new JScrollPane(detail);

      detailPane.setPreferredSize(new Dimension(900, 50));

      detailPanel.add(detailPane, BorderLayout.CENTER);

      final JToolBar detailToolbar = new JToolBar(JToolBar.HORIZONTAL);
      detailToolbar.setFloatable(false);

      final LayoutEditorPane layoutEditorPane = new LayoutEditorPane();
      final JDialog layoutEditorDialog =
        new JDialog(logUI, "Pattern Editor");
      layoutEditorDialog.getContentPane().add(layoutEditorPane);
      layoutEditorDialog.setSize(640, 480);

      layoutEditorPane.addCancelActionListener(
        new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            layoutEditorDialog.setVisible(false);
          }
        });

      layoutEditorPane.addOkActionListener(
        new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            setDetailPaneConversionPattern(
              layoutEditorPane.getConversionPattern());
            layoutEditorDialog.setVisible(false);
          }
        });

      Action editDetailAction =
        new AbstractAction(
          "Edit...", new ImageIcon(ChainsawIcons.ICON_EDIT_RECEIVER)) {
          public void actionPerformed(ActionEvent e) {
            layoutEditorPane.setConversionPattern(
              getDetailPaneConversionPattern());

            Dimension size = Toolkit.getDefaultToolkit().getScreenSize();
            Point p =
              new Point(
                ((int) ((size.getWidth() / 2)
                - (layoutEditorDialog.getSize().getWidth() / 2))),
                ((int) ((size.getHeight() / 2)
                - (layoutEditorDialog.getSize().getHeight() / 2))));
            layoutEditorDialog.setLocation(p);

            layoutEditorDialog.setVisible(true);
          }
        };

      editDetailAction.putValue(
        Action.SHORT_DESCRIPTION,
        "opens a Dialog window to Edit the Pattern Layout text");

      final SmallButton editDetailButton = new SmallButton(editDetailAction);
      editDetailButton.setText(null);
      detailToolbar.add(Box.createHorizontalGlue());
      detailToolbar.add(editDetailButton);

      //      detailToolbar.add(Box.createHorizontalStrut(5));
      detailPanel.add(detailToolbar, BorderLayout.NORTH);

      JPopupMenu editDetailPopupMenu = new JPopupMenu();
      editDetailPopupMenu.add(editDetailAction);
      editDetailPopupMenu.addSeparator();

      editDetailPopupMenu.add(
        new AbstractAction("Set to Default Layout") {
          public void actionPerformed(ActionEvent e) {
            setDetailPaneConversionPattern(
              DefaultLayoutFactory.getDefaultPatternLayout());
          }
        });

      editDetailPopupMenu.addSeparator();

      editDetailPopupMenu.add(
        new AbstractAction("Set to TCCLayout") {
          public void actionPerformed(ActionEvent e) {
            setDetailPaneConversionPattern(
              PatternLayout.TTCC_CONVERSION_PATTERN);
          }
        });

      PopupListener editDetailPopupListener =
        new PopupListener(editDetailPopupMenu);
      detail.addMouseListener(editDetailPopupListener);

      lowerPanel =
        new JSplitPane(
          JSplitPane.VERTICAL_SPLIT, eventsAndStatusPanel, detailPanel);
      lowerPanel.setBorder(null);
      lowerPanel.setDividerLocation(150);
      lowerPanel.setLastDividerLocation(-1);
      lowerPanel.setOneTouchExpandable(true);
      lowerPanel.addPropertyChangeListener(
        "dividerLocation",
        new PropertyChangeListener() {
          public void propertyChange(PropertyChangeEvent evt) {
            logUI.getToolBarAndMenus().stateChange();
          }
        });

      nameTreeAndMainPanelSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
      nameTreeAndMainPanelSplit.add(logTreePanel);
      nameTreeAndMainPanelSplit.add(lowerPanel);
      nameTreeAndMainPanelSplit.setOneTouchExpandable(true);
      nameTreeAndMainPanelSplit.setToolTipText("Still under development....");
      nameTreeAndMainPanelSplit.setDividerLocation(-1);

      add(nameTreeAndMainPanelSplit, BorderLayout.CENTER);

      /**
       * This listener deals with when the user hides the LogPanel,
       * by disabling the use of the splitpane
       */
      logTreePanel.addComponentListener(
        new ComponentListener() {
          public void componentHidden(ComponentEvent e) {
            nameTreeAndMainPanelSplit.setEnabled(false);
            nameTreeAndMainPanelSplit.setOneTouchExpandable(false);
            logUI.getToolBarAndMenus().stateChange();
          }

          public void componentMoved(ComponentEvent e) {
          }

          public void componentResized(ComponentEvent e) {
          }

          public void componentShown(ComponentEvent e) {
            nameTreeAndMainPanelSplit.setEnabled(true);
            nameTreeAndMainPanelSplit.setOneTouchExpandable(true);
            nameTreeAndMainPanelSplit.setDividerLocation(-1);
            logUI.getToolBarAndMenus().stateChange();
          }
        });

      //      add(lowerPanel, BorderLayout.CENTER);
      table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

      table.getSelectionModel().addListSelectionListener(
        new ListSelectionListener() {
          public void valueChanged(ListSelectionEvent evt) {
            if (
              ((evt.getFirstIndex() == evt.getLastIndex())
                && (evt.getFirstIndex() > 0)) || (evt.getValueIsAdjusting())) {
              return;
            }

            final ListSelectionModel lsm =
              (ListSelectionModel) evt.getSource();

            if (lsm.isSelectionEmpty()) {
              if (getIdentifier().equals(LogPanel.this.logUI.getActiveTabName())) {
                logUI.getStatusBar().setNothingSelected();
              }

              if (detail.getDocument().getDefaultRootElement() != null) {
                detailPaneUpdater.setSelectedRow(-1);
              }
            } else {
              if (table.getSelectedRow() > -1) {
                int selectedRow = table.getSelectedRow();

                if (getIdentifier().equals(LogPanel.this.logUI.getActiveTabName())) {
                  updateStatusBar();
                }

                try {
                  if (tableModel.getRowCount() >= selectedRow) {
                    detailPaneUpdater.setSelectedRow(table.getSelectedRow());
                  } else {
                    detailPaneUpdater.setSelectedRow(-1);
                  }
                } catch (Exception e) {
                  e.printStackTrace();
                  detailPaneUpdater.setSelectedRow(-1);
                }
              }
            }
          }
        });

      final JMenuItem menuItemToggleDock = new JMenuItem("Undock/dock");

      f = new JFrame(ident);
      f.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

      if (ChainsawIcons.UNDOCKED_ICON != null) {
        f.setIconImage(new ImageIcon(ChainsawIcons.UNDOCKED_ICON).getImage());
      }

      externalPanel = new DockablePanel();
      externalPanel.setLayout(new BorderLayout());
      f.getContentPane().add(externalPanel);
      f.getContentPane().add(
        logUI.getToolBarAndMenus().createDockwindowToolbar(f, this), BorderLayout.NORTH);

      dockingAction =
        new AbstractAction("Undock") {
            public void actionPerformed(ActionEvent evt) {
              if (isDocked()) {
                undock();
              } else {
                dock();
              }
            }
          };
      dockingAction.putValue(
        Action.SMALL_ICON, new ImageIcon(ChainsawIcons.UNDOCK));
      menuItemToggleDock.setAction(dockingAction);
      f.addWindowListener(
        new WindowAdapter() {
          public void windowClosing(WindowEvent e) {
            dock();
          }
        });

      JMenuItem menuItemDisplayFilter =
        new JMenuItem("Define display and color filters...");
      menuItemDisplayFilter.addActionListener(
        new ActionListener() {
          public void actionPerformed(ActionEvent evt) {
            showPreferences();
          }
        });
      menuItemDisplayFilter.setIcon(ChainsawIcons.ICON_PREFERENCES);

      final JCheckBoxMenuItem menuItemToggleToolTips =
        new JCheckBoxMenuItem(
          "Show ToolTips", tooltipsEnabled);
      menuItemToggleToolTips.addActionListener(
        new ActionListener() {
          public void actionPerformed(ActionEvent evt) {
            tooltipsEnabled = menuItemToggleToolTips.isSelected();
          }
        });
      menuItemToggleToolTips.setIcon(new ImageIcon(ChainsawIcons.TOOL_TIP));

      final JMenuItem menuDefineCustomFilter =
        new JMenuItem("Custom filter from mouse location");
      menuDefineCustomFilter.addActionListener(
        new ActionListener() {
          public void actionPerformed(ActionEvent evt) {
            if (currentPoint != null) {
              int column = table.columnAtPoint(currentPoint);
              int row = table.rowAtPoint(currentPoint);
              String colName = table.getColumnName(column);
              String value = "";

              if (
                colName.equalsIgnoreCase(ChainsawConstants.TIMESTAMP_COL_NAME)) {
                JComponent comp =
                  (JComponent) table.getCellRenderer(row, column);

                if (comp instanceof JLabel) {
                  value = ((JLabel) comp).getText();
                }
              } else {
                value = table.getValueAt(row, column).toString();
              }

              customFilterList.setSelectedItem(colName);
              filterText.setText(value);
            }
          }
        });

      final JCheckBoxMenuItem menuItemScrollBottom =
        new JCheckBoxMenuItem("Scroll to bottom", scrollToBottom.isScrolled());
      menuItemScrollBottom.addActionListener(
        new ActionListener() {
          public void actionPerformed(ActionEvent evt) {
            scrollToBottom.scroll(menuItemScrollBottom.isSelected());
          }
        });
      menuItemScrollBottom.setIcon(
        new ImageIcon(ChainsawIcons.SCROLL_TO_BOTTOM));

      JMenuItem menuItemRemoveColorFilter =
        new JMenuItem("Remove all color filters");
      menuItemRemoveColorFilter.addActionListener(
        new ActionListener() {
          public void actionPerformed(ActionEvent evt) {
//            colorDisplaySelector.clearColors();
            colorFilter.clear();
          }
        });

      JMenuItem menuItemColumnSelector =
        new JMenuItem("Select display columns...");
      menuItemColumnSelector.addActionListener(
        new ActionListener() {
          public void actionPerformed(ActionEvent evt) {
            columnSelector.show();
          }
        });

      JMenuItem menuItemRemoveDisplayFilter =
        new JMenuItem("Remove all display filters");
      menuItemRemoveDisplayFilter.addActionListener(
        new ActionListener() {
          public void actionPerformed(ActionEvent evt) {
//            colorDisplaySelector.clearDisplay();
            tableModel.setDisplayRule(null);
          }
        });

      final JPopupMenu p = new JPopupMenu();

      p.add(menuItemToggleDock);
      p.add(new JSeparator());

      final JCheckBoxMenuItem menuItemToggleDetails =
        new JCheckBoxMenuItem("Show Detail Pane");
      menuItemToggleDetails.addActionListener(
        new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            toggleDetailPanel();
            menuItemToggleDetails.getModel().setSelected(
              isDetailPaneVisible());
          }
        });
      lowerPanel.addPropertyChangeListener(
        "dividerLocation",
        new PropertyChangeListener() {
          public void propertyChange(PropertyChangeEvent evt) {
            menuItemToggleDetails.getModel().setSelected(
              isDetailPaneVisible());
          }
        });
      menuItemToggleDetails.setIcon(new ImageIcon(ChainsawIcons.INFO));

      /**
       * We set this to true first, because the Split pane hasn't been laid
       * out yet, and isDetailPaneVisible() will therefore return false.
       */
      menuItemToggleDetails.getModel().setSelected(true);
      menuItemToggleDetails.addChangeListener(logUI.getToolBarAndMenus());

      p.add(menuItemToggleDetails);
      p.add(menuItemToggleToolTips);
      p.add(menuItemScrollBottom);

      p.add(new JSeparator());

      p.add(menuDefineCustomFilter);
      p.add(new JSeparator());

      p.add(menuItemDisplayFilter);
      p.add(menuColumnDisplayFilter);
      p.add(menuColumnColorFilter);
      p.add(new JSeparator());

      JMenu removeSubMenu = new JMenu("Remove");
      JMenu selectSubMenu = new JMenu("Select");

      selectSubMenu.add(menuItemColumnSelector);

      removeSubMenu.add(menuItemRemoveColorFilter);
      removeSubMenu.add(menuItemRemoveDisplayFilter);

      p.add(selectSubMenu);
      p.add(removeSubMenu);

      final PopupListener popupListener = new PopupListener(p);

      eventsPane.addMouseListener(popupListener);
      table.addMouseListener(popupListener);

      //      detail.addMouseListener(popupListener);
      logUI.getTableMap().put(ident, table);
      logUI.getTableModelMap().put(ident, tableModel);
      logUI.getTabbedPane().add(ident, this);
      logUI.getPanelMap().put(ident, this);

      tableModel.addEventCountListener(
        new EventCountListener() {
          public void eventCountChanged(int currentCount, int totalCount) {
            statusPaneLabel.setText(
              " Events " + currentCount + " of " + totalCount);
          }
        });

      if (tableModel.isCyclic()) {
        final ChainsawCyclicBufferTableModel cyclicModel =
          (ChainsawCyclicBufferTableModel) tableModel;
        tableModel.addEventCountListener(
          new EventCountListener() {
            final NumberFormat formatter = NumberFormat.getPercentInstance();
            boolean warning75 = false;
            boolean warning100 = false;

            public void eventCountChanged(int currentCount, int totalCount) {
              double percent =
                ((double) totalCount) / cyclicModel.getMaxSize();
              String msg = null;

              if ((percent > 0.75) && (percent < 1.0) && !warning75) {
                msg =
                  "Warning :: " + formatter.format(percent) + " of the '"
                  + getIdentifier() + "' buffer has been used";
                warning75 = true;
              } else if ((percent >= 1.0) && !warning100) {
                msg =
                  "Warning :: " + formatter.format(percent) + " of the '"
                  + getIdentifier()
                  + "' buffer has been used.  Older events are being discarded.";
                warning100 = true;
              }

              if (msg != null) {
                LogPanel.this.logUI.getStatusBar().setMessage(msg);
              }
            }
          });
      }

      tableModel.addEventCountListener(logUI.new TabIconHandler(ident));
      f.pack();
    }

    /**
     * @return
     */
    public boolean isPaused() {
      return paused;
    }

    /**
    * Modifies the Paused property and notifies the listeners
     * @param paused
     */
    public void setPaused(boolean paused) {
      boolean oldValue = this.paused;
      this.paused = paused;
      firePropertyChange("paused", oldValue, paused);
    }

    void updateStatusBar() {
      SwingUtilities.invokeLater(
        new Runnable() {
          public void run() {
            logUI.getStatusBar().setSelectedLine(
              table.getSelectedRow() + 1, table.getModel().getRowCount());
          }
        });
    }

    void setDetailPaneConversionPattern(String conversionPattern) {
      ((EventDetailLayout) detailPaneLayout).setConversionPattern(
        conversionPattern);
    }

    String getDetailPaneConversionPattern() {
      return ((EventDetailLayout) detailPaneLayout).getConversionPattern();
    }

    void showPreferences() {
//      colorDisplaySelector.show();
    }

    TableModel getModel() {
      return tableModel;
    }

    String getIdentifier() {
      return identifier;
    }

    void clearModel() {
      tableModel.clearModel();

      synchronized (detail) {
        detailPaneUpdater.setSelectedRow(-1);
        detail.notify();
      }

      logUI.getStatusBar().setNothingSelected();
    }

    public int getCurrentRow() {
      return table.getSelectedRow();
    }

    /**
     * Find from the top
     * @param text
     */
    void findFromTop(String text) {
      find(0, text);
    }

    /**
     * Finds the row with the specified text, and ensures it is made visible
     * @param text
     */
    void find(String text) {
      find(table.getSelectedRow(), text);
    }

    /**
     * Finds the next row with the specified text, and ensures it is made visible
     * @param text
     */
    void findNext(String text) {
      find(table.getSelectedRow() + 1, text);
    }

    /**
     * Finds the row with the specified text starting at the specified row, and ensures it is made visible
     * @param text
     */
    void find(int row, String text) {
      final int newRow = tableModel.find(row, text);

      if (text.length() == 0) {
        scrollToBottom.bypass(false);
      } else {
        scrollToBottom.bypass(true);
      }

      table.scrollToRow(
        newRow, table.columnAtPoint(table.getVisibleRect().getLocation()));
    }

    /**
     * Docks this DockablePanel by hiding the JFrame and placing the
     * Panel back inside the LogUI window.
     *
     */
    void dock() {
      setDocked(true);
      f.setVisible(false);
      removeAll();

      //      add(lowerPanel, BorderLayout.CENTER);
      add(nameTreeAndMainPanelSplit, BorderLayout.CENTER);
      logUI.getPanelMap().put(getIdentifier(), LogPanel.this);
      this.logUI.getTabbedPane().addANewTab(getIdentifier(), LogPanel.this, null);
      externalPanel.setDocked(true);
      dockingAction.putValue(Action.NAME, "Undock");
      dockingAction.putValue(Action.SMALL_ICON, ChainsawIcons.ICON_UNDOCK);
    }

    /**
     * Undocks this DockablePanel by removing the panel from the LogUI
     * window and placing it inside it's own JFrame.
     *
     */
    void undock() {
      setDocked(false);
      externalPanel.removeAll();
      externalPanel.add(nameTreeAndMainPanelSplit, BorderLayout.CENTER);
      this.logUI.getTabbedPane().remove(LogPanel.this);
      externalPanel.setDocked(false);
      logUI.getPanelMap().put(getIdentifier(), externalPanel);
      f.setSize(getSize());

      f.setLocation(getBounds().x, getBounds().y);

      f.setVisible(true);
      dockingAction.putValue(Action.NAME, "Dock");
      dockingAction.putValue(Action.SMALL_ICON, ChainsawIcons.ICON_DOCK);
    }

    /**
     * returns true if the DetailPane is viewable, that is
     * if the SplitPane's properties are such that the lower pane
     * would be seen, and so returns a boolean expression
     * of the SplitPanes divider location
     * @return
     */
    boolean isDetailPaneVisible() {
      double currentLoc = lowerPanel.getDividerLocation();
      double max = lowerPanel.getMaximumDividerLocation();

      return currentLoc < max;
    }

    /**
     * Shows or hides the Detail Pane depending on the Last
     * known position
     *
     */
    void toggleDetailPanel() {
      int currentPosition = lowerPanel.getDividerLocation();
      int lastPosition = lowerPanel.getLastDividerLocation();

      if (lastPosition == -1) {
        lowerPanel.setDividerLocation(1.0d);
        lowerPanel.setLastDividerLocation(currentPosition);
      } else {
        lowerPanel.setDividerLocation(lowerPanel.getLastDividerLocation());
        lowerPanel.setLastDividerLocation(currentPosition);
      }
    }

    void toggleLogTreePanel() {
      LogLog.debug(
        "Toggling logPanel, currently isVisible=" + logTreePanel.isVisible());
      logTreePanel.setVisible(!logTreePanel.isVisible());
      LogLog.debug(
        "Toggling logPanel, now isVisible=" + logTreePanel.isVisible());
    }

    public void saveSettings() {
      saveColumnSettings(identifier, table.getColumnModel());
//      colorDisplaySelector.save();
//      TODO save display rule settings
//      displayFilter.save();
    }

    public void saveSettings(SaveSettingsEvent event) {
      //not used..save of columns performed via tablecolumnmodellistener event callback
    }

    void saveColumnSettings(String ident, TableColumnModel model) {
      ObjectOutputStream o = null;

      try {
        File f =
          new File(
            SettingsManager.getInstance().getSettingsDirectory()
            + File.separator + ident + LogUI.COLUMNS_EXTENSION);
        o = new ObjectOutputStream(
            new BufferedOutputStream(new FileOutputStream(f)));

        Enumeration e = model.getColumns();

        while (e.hasMoreElements()) {
          TableColumn c = (TableColumn) e.nextElement();

          if (c.getModelIndex() < ChainsawColumns.getColumnsNames().size()) {
            o.writeObject(
              new TableColumnData(
                (String) c.getHeaderValue(), c.getModelIndex(), c.getWidth()));
          } else {
            LogLog.debug(
              "Not saving col ' " + c.getHeaderValue()
              + "' not part of standard columns");
          }
        }

        o.flush();
      } catch (FileNotFoundException fnfe) {
        fnfe.printStackTrace();
      } catch (IOException ioe) {
        ioe.printStackTrace();
      } finally {
        try {
          if (o != null) {
            o.close();
          }
        } catch (IOException ioe) {
          ioe.printStackTrace();
        }
      }
    }

    /* (non-Javadoc)
     * @see org.apache.log4j.chainsaw.prefs.Profileable#loadSettings(org.apache.log4j.chainsaw.prefs.LoadSettingsEvent)
     */
    public void loadSettings(LoadSettingsEvent event) {
      File f =
        new File(
          SettingsManager.getInstance().getSettingsDirectory()
          + File.separator + identifier + LogUI.COLUMNS_EXTENSION);

      if (f.exists()) {
        loadColumnSettings(identifier, table.getColumnModel());
      } else {
        loadDefaultColumnSettings(event);
      }
    }

    void loadDefaultColumnSettings(LoadSettingsEvent event) {
      String columnOrder = event.getSetting(LogUI.TABLE_COLUMN_ORDER);

      TableColumnModel columnModel = table.getColumnModel();

      Map columnNameMap = new HashMap();

      for (int i = 0; i < columnModel.getColumnCount(); i++) {
        columnNameMap.put(table.getColumnName(i), columnModel.getColumn(i));
      }

      int index = 0;
      StringTokenizer tok = new StringTokenizer(columnOrder, ",");
      List sortedColumnList = new ArrayList();

      //remove all columns from the table that exist in the model
      //and add in the correct order to a new arraylist
      //(may be a subset of possible columns)
      while (tok.hasMoreElements()) {
        String element = (String) tok.nextElement();
        TableColumn column = (TableColumn) columnNameMap.get(element);

        if (column != null) {
          System.out.println(
            "Moving column " + element + " from index "
            + column.getModelIndex() + " to index " + index++);
          sortedColumnList.add(column);
          table.removeColumn(column);
        }
      }

      //re-add columns to the table in the order provided from the list
      for (Iterator iter = sortedColumnList.iterator(); iter.hasNext();) {
        TableColumn element = (TableColumn) iter.next();
        table.addColumn(element);
      }

      //    TODO Rest of the load settings
      String columnWidths = event.getSetting(LogUI.TABLE_COLUMN_WIDTHS);

      //    System.out.println("Column widths=" + columnWidths);
      tok = new StringTokenizer(columnWidths, ",");
      index = 0;

      while (tok.hasMoreElements()) {
        String element = (String) tok.nextElement();

        try {
          int width = Integer.parseInt(element);

          if (index > (columnModel.getColumnCount() - 1)) {
            System.out.println(
              "loadsettings - failed attempt to set width for index " + index
              + ", width " + element);
          } else {
            columnModel.getColumn(index).setPreferredWidth(width);
          }

          index++;
        } catch (NumberFormatException e) {
          LogLog.error("Error decoding a Table width", e);
        }
      }

      SwingUtilities.invokeLater(
        new Runnable() {
          public void run() {
            repaint();
          }
        });
    }

    void loadColumnSettings(String ident, TableColumnModel model) {
      File f =
        new File(
          SettingsManager.getInstance().getSettingsDirectory()
          + File.separator + ident + LogUI.COLUMNS_EXTENSION);

      if (f.exists()) {
        ArrayList newColumns = new ArrayList();

        TableColumnData temp = null;
        ObjectInputStream s = null;

        try {
          s = new ObjectInputStream(
              new BufferedInputStream(new FileInputStream(f)));

          while (true) {
            temp = (TableColumnData) s.readObject();

            TableColumn tc = new TableColumn(temp.getIndex(), temp.getWidth());
            tc.setHeaderValue(temp.getColName());
            newColumns.add(tc);
          }
        } catch (EOFException eof) { //end of file - ignore..
        }catch (IOException ioe) {
          ioe.printStackTrace();
        } catch (ClassNotFoundException cnfe) {
          cnfe.printStackTrace();
        } finally {
          if (s != null) {
            try {
              s.close();
            } catch (IOException ioe) {
              ioe.printStackTrace();
            }
          }
        }

        //only remove columns and add serialized columns if 
        //at least one column was read from the file
        if (newColumns.size() > 0) {
          //remove columns from model - will be re-added in the correct order
          for (int i = model.getColumnCount() - 1; i > -1; i--) {
            model.removeColumn(model.getColumn(i));
          }

          for (Iterator iter = newColumns.iterator(); iter.hasNext();) {
            model.addColumn((TableColumn) iter.next());
          }
        }
      }
    }

    /**
     * @return
     */
    public boolean isLogTreePanelVisible() {
      return logTreePanel.isVisible();
    }

    /* (non-Javadoc)
     * @see org.apache.log4j.chainsaw.EventBatchListener#getInterestedIdentifier()
     */
    public String getInterestedIdentifier() {
      return getIdentifier();
    }

    /* (non-Javadoc)
     * @see org.apache.log4j.chainsaw.EventBatchListener#receiveEventBatch(java.lang.String, java.util.List)
     */
    public void receiveEventBatch(String identifier, List eventBatchEntrys) {
      /**
       * if this panel is paused, we totally ignore events
       */
      if (isPaused()) {
        return;
      }

      table.getSelectionModel().setValueIsAdjusting(true);

      boolean rowAdded = false;
      LoggingEvent lastSelected = null;

      if (table.getSelectedRow() > -1) {
        lastSelected = tableModel.getRow(table.getSelectedRow());
      }

      for (Iterator iter = eventBatchEntrys.iterator(); iter.hasNext();) {
        ChainsawEventBatchEntry entry = (ChainsawEventBatchEntry) iter.next();

        //        Vector v = formatFields(entry.getEventVector());
        final String eventType = entry.getEventType();

        updateEntryMap(entry);

        boolean isCurrentRowAdded =
          tableModel.isAddRow(entry.getEvent(), true);
        rowAdded = rowAdded ? true : isCurrentRowAdded;
      }

      table.getSelectionModel().setValueIsAdjusting(false);

      //tell the model to notify the count listeners
      tableModel.notifyCountListeners();

      if (rowAdded) {
        tableModel.sort();

        if (scrollToBottom.isScrolled() && !scrollToBottom.isBypassed()) {
          table.scrollToBottom(
            table.columnAtPoint(table.getVisibleRect().getLocation()));
        } else {
          if (lastSelected != null) {
            table.scrollToRow(
              tableModel.getRowIndex(lastSelected),
              table.columnAtPoint(table.getVisibleRect().getLocation()));
          }
        }
      }
    }

    /**
     * ensures the Entry map of all the unque logger names etc, that is used for the Filter panel is
     * updated with any new information from the event
     * @param v
     * @param eventType
     * @param level
     */
    private void updateEntryMap(ChainsawEventBatchEntry entry) {
      LoggingEvent event = entry.getEvent();
      String eventType = entry.getEventType();
      String level = event.getLevel().toString();

      //add the level to the appropriate list if it didn't previously exist
      if (!((List) logUI.getLevelMap().get(eventType)).contains(level)) {
        ((List) logUI.getLevelMap().get(eventType)).add(level);
      }

      Map map = (HashMap) logUI.getEntryMap().get(getIdentifier());

      //        TODO fix up this Set Cast-O-Rama
      //also add it to the unique values list
      ((Set) map.get(ChainsawConstants.LEVEL_COL_NAME)).add(level);

      Object loggerName = event.getLoggerName();
      ((Set) map.get(ChainsawConstants.LOGGER_COL_NAME)).add(loggerName);

      /**
       * EventContainer is a LoggerNameModel imp, use that for notifing
       */
      tableModel.addLoggerName(loggerName.toString());

      ((Set) map.get(ChainsawConstants.THREAD_COL_NAME)).add(
        event.getThreadName());
      ((Set) map.get(ChainsawConstants.NDC_COL_NAME)).add(event.getNDC());

      //          TODO MDC event stuff is not being output correctly
      ((Set) map.get(ChainsawConstants.MDC_COL_NAME)).add(
        event.getMDCKeySet());

      if (event.getLocationInformation() != null) {
        LocationInfo info = event.getLocationInformation();
        ((Set) map.get(ChainsawConstants.CLASS_COL_NAME)).add(
          info.getClassName());
        ((Set) map.get(ChainsawConstants.METHOD_COL_NAME)).add(
          info.getMethodName());
        ((Set) map.get(ChainsawConstants.FILE_COL_NAME)).add(
          info.getFileName());
      }
    }

    /**
     * @return
     */
    public final Layout getDetailPaneLayout() {
      return detailPaneLayout;
    }

    /**
     * @param detailPaneLayout
     */
    public final void setDetailPaneLayout(Layout detailPaneLayout) {
      this.detailPaneLayout = detailPaneLayout;
    }

    /**
     * @return
     */
    public final Layout getToolTipLayout() {
      return toolTipLayout;
    }

    /**
     * @param toolTipLayout
     */
    public final void setToolTipLayout(Layout toolTipLayout) {
      this.toolTipLayout = toolTipLayout;
    }

	class TableColumnData implements Serializable {
	  static final long serialVersionUID = 5350440293110513986L;
	  private String colName;
	  private int index;
	  private int width;

	  public TableColumnData(String colName, int index, int width) {
		this.colName = colName;
		this.index = index;
		this.width = width;
	  }

	  public String getColName() {
		return colName;
	  }

	  public int getIndex() {
		return index;
	  }

	  public int getWidth() {
		return width;
	  }

	  private void readObject(java.io.ObjectInputStream in)
		throws IOException, ClassNotFoundException {
		colName = (String) in.readObject();
		index = in.readInt();
		width = in.readInt();
	  }

	  private void writeObject(java.io.ObjectOutputStream out)
		throws IOException {
		out.writeObject(colName);
		out.writeInt(index);
		out.writeInt(width);
	  }
	}


	//if columnmoved or columnremoved callback received, re-apply table's sort index based
	//sort column name
	class ChainsawTableColumnModelListener implements TableColumnModelListener {
	  private JSortTable table;

	  public ChainsawTableColumnModelListener(JSortTable table) {
		this.table = table;
	  }

	  public void columnAdded(TableColumnModelEvent e) {
		LogLog.debug("Detected columnAdded" + e);
	  }

	  public void columnRemoved(TableColumnModelEvent e) {
		table.updateSortedColumn();
	  }

	  public void columnMoved(TableColumnModelEvent e) {
		table.updateSortedColumn();
	  }

	  public void columnMarginChanged(ChangeEvent e) {
	  }

	  public void columnSelectionChanged(ListSelectionEvent e) {
	  }
	}

	/**
	 * Thread that periodically checks if the selected row has changed, and if
	 * it was, updates the Detail Panel with the detailed Logging information
	 */
	class DetailPaneUpdater {
	  private int selectedRow = -1;
	  private int lastRow = -1;
	  private final JEditorPane pane;
	  private final EventContainer model;
	  private final LogPanel panel;

	  public DetailPaneUpdater(
		LogPanel panel, JEditorPane pane, EventContainer model) {
		this.pane = pane;
		this.model = model;
		this.panel = panel;
	  }

	  public void setSelectedRow(int row) {
		if (row == -1) {
		  lastRow = 0;
		}

		selectedRow = row;
		updateDetailPane();
	  }

	  private void updateDetailPane() {
		String text = null;

		if (selectedRow != lastRow) {
		  if (selectedRow == -1) {
			text = "Nothing selected";
		  } else {
			LoggingEvent event = model.getRow(selectedRow);

			if (event != null) {
			  Layout layout = panel.getDetailPaneLayout();
			  StringBuffer buf = new StringBuffer();
			  buf.append(layout.getHeader()).append(layout.format(event)).append(
				layout.getFooter());
			  text = buf.toString();
			}
		  }

		  if (!((text != null) && !text.equals(""))) {
			text = "Nothing selected";
		  }

		  lastRow = selectedRow;

		  final String text2 = text;
		  SwingUtilities.invokeLater(
			new Runnable() {
			  public void run() {
				pane.setText(text2);
			  }
			});
		  SwingUtilities.invokeLater(
			new Runnable() {
			  public void run() {
				pane.setCaretPosition(0);
			  }
			});
		}
	  }
	}

	class ScrollToBottom extends Thread {
	  boolean scrollToBottom;
	  boolean bypassed;

	  public ScrollToBottom(boolean scrollToBottom) {
		this.scrollToBottom = scrollToBottom;
	  }

	  public void scroll(boolean scrollToBottom) {
		this.scrollToBottom = scrollToBottom;
	  }

	  public boolean isScrolled() {
		return scrollToBottom;
	  }

	  public void bypass(boolean bypassed) {
		this.bypassed = bypassed;
	  }

	  public boolean isBypassed() {
		return bypassed;
	  }
	}

  }