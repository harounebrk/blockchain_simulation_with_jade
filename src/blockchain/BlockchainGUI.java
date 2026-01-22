package blockchain;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;
import java.util.Map;

import javax.swing.text.Document;
import javax.swing.text.html.HTMLDocument;


public class BlockchainGUI extends JFrame {

    // === Node Agent Reference ===
	private NodeAgent agent;
	
	public void setAgent(NodeAgent agent) {
	    this.agent = agent;
	}
	
	public NodeAgent getAgent() {
	    return agent;
	}
	
	// === Utility GUI update methods ===
    public void log(String message) {
        logsArea.append(message + "\n");
    }

    public void showCurrentBlock(String blockInfo) {
        currentBlockArea.setText(blockInfo);
    }

    public void showProofOfWork(String powInfo) {
        powArea.setText(powInfo);
    }
    
    public void appendProofOfWork(String message) {
    	powArea.append(message + "\n");
    }
    
    public void setInfo(String text) {
    	infoDetailsLabel.setText(text);
    }
    
    public void appendInfoLine(String message) {
        // Get current text, remove </html> at the end
        String currentText = infoDetailsLabel.getText();
        currentText = currentText.substring(0, currentText.length() - 7); // remove </html>

        // Append new message with a <br> for line break
        currentText += "<br>" + message + "</html>";

        // Update the label
        infoDetailsLabel.setText(currentText);
    }
    
    public void updateInfoLine(String key, String newValue) {
        String[] lines = infoDetailsLabel.getText().split("<br>");

        for (int i = 0; i < lines.length; i++) {
            if (lines[i].startsWith("<b>" + key + ":")) {
                lines[i] = "<b>" + key + ": </b>" + newValue;
                break;
            }
        }

        infoDetailsLabel.setText(String.join("<br>", lines));
    }
    
    public void updateCurrentBlockLine(String key, String value) {
        String[] lines = currentBlockArea.getText().split("<br>");

        for (int i = 0; i < lines.length; i++) {
        	if (lines[i].startsWith("<b>" + key + ":")) {
                lines[i] = "<b>" + key + ": </b>" + value;
                break;
            }
        }

        currentBlockArea.setText(String.join("<br>", lines));
    }
    
    
    // === GUI DIALOG BOXES ===
    
    // --- Create Transaction Dialog Box ---
    public Object[] createTransactionDialog(List<String> recipients) {

        JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(2, 2, 10, 10));

        JLabel amountLabel = new JLabel("Amount:");
        JTextField amountField = new JTextField();

        JLabel recipientLabel = new JLabel("Recipient:");
        JComboBox<String> recipientBox = new JComboBox<>();

        for (String r : recipients) {
            recipientBox.addItem(r);
        }

        panel.add(amountLabel);
        panel.add(amountField);
        panel.add(recipientLabel);
        panel.add(recipientBox);

        int result = JOptionPane.showConfirmDialog(
                null,
                panel,
                "Create Transaction",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (result == JOptionPane.OK_OPTION) {
            return new Object[] {
                amountField.getText(),
                recipientBox.getSelectedItem()
            };
        }

        return null;
    }
    
    // --- Send Transaction Dialog Box ---
    public Transaction SendTransactionDialog(
            List<Transaction> txList,
            Map<String, String> knownNodes,
            String myAddress
    ) {
        JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(5, 1, 2, 2));

        // Title label
        JLabel selLabel = new JLabel("Select a transaction to send:");
        JComboBox<String> combo = new JComboBox<>();

        for (Transaction t : txList)
            combo.addItem(t.getId());

        // Dynamic labels
        JLabel recipientLabel = new JLabel("Recipient: ");
        JLabel amountLabel = new JLabel("Amount: ");
        JLabel changeLabel = new JLabel("Change: ");

        panel.add(selLabel);
        panel.add(combo);
        panel.add(recipientLabel);
        panel.add(amountLabel);
        panel.add(changeLabel);

        // On selection change → update all labels
        combo.addActionListener(ev -> {
            int idx = combo.getSelectedIndex();
            if (idx < 0) return;

            Transaction tx = txList.get(idx);
            List<TransactionOutput.Output> outs = tx.getTxOutput().getOutputList();

            // Determine recipient output
            TransactionOutput.Output recipientOut =
                    outs.get(0).getScriptPubKey().equals(myAddress)
                            ? (outs.size() > 1 ? outs.get(1) : outs.get(0))
                            : outs.get(0);

            String recipientAddr = recipientOut.getScriptPubKey();

            // Map address → node name if known
            String recipientName = knownNodes.entrySet()
                    .stream()
                    .filter(e -> e.getValue().equals(recipientAddr))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(recipientAddr);

            recipientLabel.setText("Recipient: " + recipientName);
            amountLabel.setText("Amount: " + recipientOut.getValue() + " BTC");

            // Determine change
            double change = outs.stream()
                    .filter(o -> o.getScriptPubKey().equals(myAddress))
                    .mapToDouble(TransactionOutput.Output::getValue)
                    .sum();

            changeLabel.setText("Transaction change: " + change + " BTC");
        });

        // Pre-select first
        if (!txList.isEmpty())
            combo.setSelectedIndex(0);

        int res = JOptionPane.showConfirmDialog(
                null,
                panel,
                "Send Transaction",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (res == JOptionPane.OK_OPTION) {
            int i = combo.getSelectedIndex();
            if (i >= 0) return txList.get(i);
        }

        return null;
    }

    // --- Verify transaction dialog ---
    public Transaction verifyTransactionDialog(List<Transaction> txList) {
        if (txList.isEmpty()) return null;

        JPanel panel = new JPanel(new BorderLayout(5, 5));

        JLabel label = new JLabel("Select a transaction to verify:");
        panel.add(label, BorderLayout.NORTH);

        // Combo box containing transaction IDs
        JComboBox<String> combo = new JComboBox<>(
                txList.stream().map(Transaction::getId).toArray(String[]::new)
        );
        panel.add(combo, BorderLayout.CENTER);

        int option = JOptionPane.showConfirmDialog(
                null,
                panel,
                "Verify Transaction",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (option != JOptionPane.OK_OPTION) return null; // user cancelled

        String selectedId = (String) combo.getSelectedItem();

        for (Transaction tx : txList) {
            if (tx.getId().equals(selectedId)) return tx;
        }

        return null;
    }
    
    // --- delete transaction dialog ---
    public Transaction deleteTransactionDialog(List<Transaction> txList) {
        if (txList.isEmpty()) return null;

        JPanel panel = new JPanel(new BorderLayout(5, 5));

        JLabel label = new JLabel("Select a pending transaction to delete:");
        panel.add(label, BorderLayout.NORTH);

        // Combo box containing transaction IDs
        JComboBox<String> combo = new JComboBox<>(
                txList.stream().map(Transaction::getId).toArray(String[]::new)
        );
        panel.add(combo, BorderLayout.CENTER);

        int option = JOptionPane.showConfirmDialog(
                null,
                panel,
                "Delete Transaction",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (option != JOptionPane.OK_OPTION) return null; // user cancelled

        String selectedId = (String) combo.getSelectedItem();

        for (Transaction tx : txList) {
            if (tx.getId().equals(selectedId)) return tx;
        }

        return null;
    }
    
    public void displayResult(String message, boolean valid) {
        int messageType = valid ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE;

        JOptionPane.showMessageDialog(
                null,
                message,
                valid ? "Information message" : "Error message",
                messageType
        );
    }

	
	// === Core Text Areas ===
    private JTextArea logsArea;
    private JTextArea powArea;
    private JEditorPane currentBlockArea;
    private JLabel infoDetailsLabel;

    // === Colors and Fonts ===
    private final Color APP_BG = new Color(240, 242, 245);
    private final Color SECTION_BG = Color.WHITE;
    private final Color TITLE_COLOR = new Color(45, 45, 45);
    private final Color BUTTON_COLOR = new Color(90, 140, 255);
    private final Color BUTTON_HOVER = new Color(70, 120, 235);
    private final Color BUTTON_PRESS = new Color(50, 100, 215);
    private final Font TITLE_FONT = new Font("Segoe UI", Font.BOLD, 18);
    private final Font TEXT_FONT = new Font("Consolas", Font.PLAIN, 15);
    private final Font BUTTON_FONT = new Font("Segoe UI", Font.BOLD, 15);
    
    public BlockchainGUI() {
        
    	// === Window Setup ===
        setTitle("Blockchain GUI");
        setSize(1200, 750);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setBackground(APP_BG);
        setLayout(new BorderLayout(20, 20));

        // === Add Sections ===
        add(createTopSection(), BorderLayout.NORTH);
        add(createMainSection(), BorderLayout.CENTER);

        setVisible(true);
    }

    // ============================================================
    // TOP SECTION (Information Panel)
    // ============================================================
    private JPanel createTopSection() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(APP_BG);
        wrapper.setBorder(new EmptyBorder(15, 30, 10, 30));

        RoundedPanel infoPanel = new RoundedPanel(25, SECTION_BG);
        infoPanel.setBorder(new EmptyBorder(25, 20, 25, 20));
        infoPanel.setLayout(new BorderLayout());

        JLabel title = new JLabel("INFORMATION ON THE BLOCKCHAIN", SwingConstants.CENTER);
        title.setFont(TITLE_FONT);
        title.setForeground(TITLE_COLOR);
        
        infoDetailsLabel = new JLabel("No node connected yet", SwingConstants.LEFT);
        infoDetailsLabel.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        infoDetailsLabel.setForeground(new Color(80, 80, 80));
        //infoDetailsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        JPanel centerPanel = new JPanel();
        centerPanel.setOpaque(false);
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));

        centerPanel.add(Box.createVerticalStrut(20)); // gap above
        centerPanel.add(infoDetailsLabel);
        centerPanel.add(Box.createVerticalStrut(5));
        
        infoPanel.add(title, BorderLayout.NORTH);
        infoPanel.add(centerPanel, BorderLayout.CENTER);
        
        wrapper.add(infoPanel, BorderLayout.CENTER);

        return wrapper;
    }

    // ============================================================
    // MAIN SECTION (Buttons + Current Block + POW & Logs)
    // ============================================================
    private JPanel createMainSection() {
        JPanel main = new JPanel(new BorderLayout(25, 25));
        main.setBackground(APP_BG);
        main.setBorder(new EmptyBorder(10, 30, 30, 30));

        // Left = Buttons
        main.add(createButtonsPanel(), BorderLayout.WEST);

        // Center = Current Block
        main.add(createCurrentBlockSection(), BorderLayout.CENTER);

        // Right = Proof of Work + Logs
        main.add(createRightPanel(), BorderLayout.EAST);

        return main;
    }

    // ============================================================
    // BUTTONS PANEL (Left Column)
    // ============================================================
    private JPanel createButtonsPanel() {
        RoundedPanel panel = new RoundedPanel(25, SECTION_BG);
        panel.setBorder(new EmptyBorder(25, 35, 25, 35));
        panel.setLayout(new GridLayout(7, 1, 15, 15));

        String[] labels = {
                "Create Transaction",
                "Send Transaction",
                "Create Block",
                "Mine Block",
                "Send Block",
                "Verify Transaction",
                "Delete Transaction"
        };

        for (String label : labels) {
            JButton btn = createRoundedButton(label);
            
            btn.addActionListener(e -> {
            	if (agent == null) {
            		JOptionPane.showMessageDialog(this, "Agent not connected yet!");
                    return;
            	}
            	
            	switch (label) {
	                case "Create Transaction" -> agent.createTransaction();
	                case "Send Transaction" -> agent.sendTransaction();
	                case "Create Block" -> agent.createBlock();
	                case "Send Block" -> agent.sendBlock();
	                case "Mine Block" -> agent.mineBlock();
	                case "Verify Transaction" -> agent.verifyTransaction();
	                case "Delete Transaction" -> agent.deleteTransaction();
            	}
            });
            
            panel.add(btn);
        }

        return panel;
    }

    // ============================================================
    // CURRENT BLOCK SECTION (Center)
    // ============================================================
    private JPanel createCurrentBlockSection() {
        RoundedPanel panel = new RoundedPanel(25, SECTION_BG);
        panel.setBorder(new EmptyBorder(15, 20, 15, 20));
        panel.setLayout(new BorderLayout());

        JLabel title = new JLabel("CURRENT BLOCK", SwingConstants.CENTER);
        title.setFont(TITLE_FONT);
        title.setForeground(TITLE_COLOR);
        panel.add(title, BorderLayout.NORTH);

        currentBlockArea = new JEditorPane();
        currentBlockArea.setContentType("text/html");
        currentBlockArea.setEditable(false);
        currentBlockArea.setBackground(new Color(250,250,250));
        currentBlockArea.setFont(TEXT_FONT);
        
        currentBlockArea.setText("<html></html>");
        
        Document doc = currentBlockArea.getDocument();
        if (doc instanceof HTMLDocument htmlDoc) {
            htmlDoc.getStyleSheet().addRule("body { font-family: 'Segoe UI'; font-size: 12px; }");
        }

        JScrollPane scrollPane = createRoundedScrollPaneForEditorPane(currentBlockArea);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }

    // ============================================================
    // RIGHT PANEL (Proof of Work + Logs)
    // ============================================================
    private JPanel createRightPanel() {
        JPanel panel = new JPanel(new GridLayout(2, 1, 25, 25));
        panel.setBackground(APP_BG);
        panel.setPreferredSize(new Dimension(420, 0));

        // Proof of Work
        JPanel powPanel = new RoundedPanel(25, SECTION_BG);
        powPanel.setBorder(new EmptyBorder(15, 20, 15, 20));
        powPanel.setLayout(new BorderLayout());

        JLabel powLabel = new JLabel("PROOF OF WORK", SwingConstants.CENTER);
        powLabel.setFont(TITLE_FONT);
        powLabel.setForeground(TITLE_COLOR);
        powPanel.add(powLabel, BorderLayout.NORTH);

        powArea = createStyledTextArea();
        powPanel.add(createRoundedScrollPane(powArea), BorderLayout.CENTER);

        // Logs
        JPanel logsPanel = new RoundedPanel(25, SECTION_BG);
        logsPanel.setBorder(new EmptyBorder(15, 20, 15, 20));
        logsPanel.setLayout(new BorderLayout());

        JLabel logsLabel = new JLabel("LOGS", SwingConstants.CENTER);
        logsLabel.setFont(TITLE_FONT);
        logsLabel.setForeground(TITLE_COLOR);
        logsPanel.add(logsLabel, BorderLayout.NORTH);

        logsArea = createStyledTextArea();
        logsPanel.add(createRoundedScrollPane(logsArea), BorderLayout.CENTER);

        panel.add(powPanel);
        panel.add(logsPanel);

        return panel;
    }

    // ============================================================
    // HELPERS
    // ============================================================
    private JTextArea createStyledTextArea() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setFont(TEXT_FONT);
        area.setBackground(new Color(250, 250, 250));
        area.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        return area;
    }

    private JScrollPane createRoundedScrollPane(JTextArea area) {
        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);
        return scroll;
    }
    
    private JScrollPane createRoundedScrollPaneForEditorPane(JEditorPane area) {
        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);
        return scroll;
    }

    private JButton createRoundedButton(String text) {
        JButton button = new JButton(text) {
			private static final long serialVersionUID = 1L;
			private Color currentColor = BUTTON_COLOR;

            {
                setOpaque(false);
                setFocusPainted(false);
                setBorderPainted(false);
                setForeground(Color.WHITE);
                setFont(BUTTON_FONT);
                setPreferredSize(new Dimension(200, 50));
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

                addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override
                    public void mouseEntered(java.awt.event.MouseEvent e) {
                        currentColor = BUTTON_HOVER;
                        repaint();
                    }

                    @Override
                    public void mouseExited(java.awt.event.MouseEvent e) {
                        currentColor = BUTTON_COLOR;
                        repaint();
                    }

                    @Override
                    public void mousePressed(java.awt.event.MouseEvent e) {
                        currentColor = BUTTON_PRESS;
                        repaint();
                    }

                    @Override
                    public void mouseReleased(java.awt.event.MouseEvent e) {
                        currentColor = BUTTON_HOVER;
                        repaint();
                    }
                });
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(currentColor);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 25, 25);

                FontMetrics fm = g2.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(getText())) / 2;
                int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.setColor(Color.WHITE);
                g2.drawString(getText(), x, y);
                g2.dispose();
            }
        };

        return button;
    }

    // ============================================================
    // ROUNDED PANEL
    // ============================================================
    private static class RoundedPanel extends JPanel {
        private final int radius;
        private final Color bgColor;

        public RoundedPanel(int radius, Color bgColor) {
            this.radius = radius;
            this.bgColor = bgColor;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(bgColor);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            g2.setColor(new Color(200, 200, 200));
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ============================================================
    // MAIN
    // ============================================================
    public static void main(String[] args) {
        SwingUtilities.invokeLater(BlockchainGUI::new);
    }
}
