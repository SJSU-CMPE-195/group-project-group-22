package edu.sjsu.spring2026.group32.launcher;

import edu.sjsu.spring2026.group32.hardware.serial.SerialConnectionManager;
import org.junit.jupiter.api.*;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit / integration tests for {@link ConnectionStatusPanel}.
 *
 * <p>All Swing construction and mutation is executed on the EDT via
 * {@link SwingUtilities#invokeAndWait} to satisfy Swing's single-thread rule.
 * On CI these tests run under {@code xvfb-run} which provides a virtual
 * X11 display.</p>
 */
@DisplayName("ConnectionStatusPanel Suite")
class ConnectionStatusPanelTest {

    private ConnectionStatusPanel panel;

    @BeforeEach
    void setUp() throws Exception {
        SwingUtilities.invokeAndWait(() -> panel = new ConnectionStatusPanel());
    }

    // =========================================================================
    //  Construction
    // =========================================================================

    @Test
    @DisplayName("Panel constructs without error")
    void constructsWithoutError() {
        assertNotNull(panel, "ConnectionStatusPanel should be non-null after construction");
    }

    @Test
    @DisplayName("Panel uses BorderLayout so indicators and button occupy separate slots")
    void usesBorderLayout() {
        assertInstanceOf(BorderLayout.class, panel.getLayout(),
            "Panel should use BorderLayout to support left-indicators + right-button");
    }

    // =========================================================================
    //  addDevice — null manager
    // =========================================================================

    @Test
    @DisplayName("addDevice with null manager shows 'Not connected' text")
    void addDevice_nullManager_showsNotConnectedText() throws Exception {
        SwingUtilities.invokeAndWait(() -> panel.addDevice("HTZ", null));

        JLabel label = firstLabel(panel);
        assertNotNull(label, "An indicator label should be present");
        assertTrue(label.getText().contains("Not connected"),
            "Null manager should produce a 'Not connected' label");
    }

    @Test
    @DisplayName("addDevice with null manager shows red indicator colour")
    void addDevice_nullManager_showsRedColour() throws Exception {
        SwingUtilities.invokeAndWait(() -> panel.addDevice("HTZ", null));

        assertEquals(Color.RED, firstLabel(panel).getForeground(),
            "Null manager should produce a red indicator");
    }

    // =========================================================================
    //  addDevice — connected manager
    // =========================================================================

    @Test
    @DisplayName("addDevice with connected manager shows 'Connected' text")
    void addDevice_connectedManager_showsConnectedText() throws Exception {
        SerialConnectionManager mgr = mock(SerialConnectionManager.class);
        when(mgr.isConnected()).thenReturn(true);

        SwingUtilities.invokeAndWait(() -> panel.addDevice("HTZ", mgr));

        JLabel label = firstLabel(panel);
        assertTrue(label.getText().contains("Connected"),
            "Connected manager should produce a 'Connected' label");
    }

    @Test
    @DisplayName("addDevice with connected manager shows green indicator colour")
    void addDevice_connectedManager_showsGreenColour() throws Exception {
        SerialConnectionManager mgr = mock(SerialConnectionManager.class);
        when(mgr.isConnected()).thenReturn(true);

        SwingUtilities.invokeAndWait(() -> panel.addDevice("HTZ", mgr));

        assertEquals(new Color(40, 190, 40), firstLabel(panel).getForeground(),
            "Connected manager should produce a green indicator");
    }

    // =========================================================================
    //  addDevice — disconnected manager
    // =========================================================================

    @Test
    @DisplayName("addDevice with disconnected manager shows 'Not connected' text")
    void addDevice_disconnectedManager_showsNotConnectedText() throws Exception {
        SerialConnectionManager mgr = mock(SerialConnectionManager.class);
        when(mgr.isConnected()).thenReturn(false);

        SwingUtilities.invokeAndWait(() -> panel.addDevice("HTZ", mgr));

        assertTrue(firstLabel(panel).getText().contains("Not connected"),
            "Disconnected manager should produce a 'Not connected' label");
    }

    @Test
    @DisplayName("addDevice includes the device name in the indicator text")
    void addDevice_includesDeviceNameInLabel() throws Exception {
        SwingUtilities.invokeAndWait(() -> panel.addDevice("Pong", null));

        assertTrue(firstLabel(panel).getText().contains("Pong"),
            "The device name passed to addDevice() should appear in the label text");
    }

    // =========================================================================
    //  Multiple devices
    // =========================================================================

    @Test
    @DisplayName("Two addDevice calls produce exactly two indicator labels")
    void twoDevices_produceTwoLabels() throws Exception {
        SerialConnectionManager mgr = mock(SerialConnectionManager.class);
        when(mgr.isConnected()).thenReturn(true);

        SwingUtilities.invokeAndWait(() -> {
            panel.addDevice("HTZ",  mgr);
            panel.addDevice("Pong", null);
        });

        assertEquals(2, allLabels(panel).size(),
            "Two addDevice() calls should produce exactly two indicator labels");
    }

    @Test
    @DisplayName("Each device label contains its own name (not the other device's)")
    void twoDevices_labelsContainCorrectNames() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            panel.addDevice("HTZ",  null);
            panel.addDevice("Pong", null);
        });

        List<JLabel> labels = allLabels(panel);
        assertEquals(2, labels.size());
        assertTrue(labels.get(0).getText().contains("HTZ"),
            "First label should contain 'HTZ'");
        assertTrue(labels.get(1).getText().contains("Pong"),
            "Second label should contain 'Pong'");
    }

    // =========================================================================
    //  refresh()
    // =========================================================================

    @Test
    @DisplayName("refresh() updates label text when manager disconnects")
    void refresh_updatesTextOnDisconnect() throws Exception {
        SerialConnectionManager mgr = mock(SerialConnectionManager.class);
        when(mgr.isConnected()).thenReturn(true);
        SwingUtilities.invokeAndWait(() -> panel.addDevice("HTZ", mgr));

        when(mgr.isConnected()).thenReturn(false);
        SwingUtilities.invokeAndWait(() -> panel.refresh());

        assertTrue(firstLabel(panel).getText().contains("Not connected"),
            "refresh() should update the label when the manager disconnects");
    }

    @Test
    @DisplayName("refresh() updates label text when manager reconnects")
    void refresh_updatesTextOnReconnect() throws Exception {
        SerialConnectionManager mgr = mock(SerialConnectionManager.class);
        when(mgr.isConnected()).thenReturn(false);
        SwingUtilities.invokeAndWait(() -> panel.addDevice("HTZ", mgr));

        when(mgr.isConnected()).thenReturn(true);
        SwingUtilities.invokeAndWait(() -> panel.refresh());

        assertTrue(firstLabel(panel).getText().contains("Connected"),
            "refresh() should update the label when the manager reconnects");
    }

    @Test
    @DisplayName("refresh() changes indicator colour from green to red on disconnect")
    void refresh_updatesColourOnDisconnect() throws Exception {
        SerialConnectionManager mgr = mock(SerialConnectionManager.class);
        when(mgr.isConnected()).thenReturn(true);
        SwingUtilities.invokeAndWait(() -> panel.addDevice("HTZ", mgr));

        when(mgr.isConnected()).thenReturn(false);
        SwingUtilities.invokeAndWait(() -> panel.refresh());

        assertEquals(Color.RED, firstLabel(panel).getForeground(),
            "Indicator should turn red after disconnect");
    }

    @Test
    @DisplayName("refresh() changes indicator colour from red to green on reconnect")
    void refresh_updatesColourOnReconnect() throws Exception {
        SerialConnectionManager mgr = mock(SerialConnectionManager.class);
        when(mgr.isConnected()).thenReturn(false);
        SwingUtilities.invokeAndWait(() -> panel.addDevice("HTZ", mgr));

        when(mgr.isConnected()).thenReturn(true);
        SwingUtilities.invokeAndWait(() -> panel.refresh());

        assertEquals(new Color(40, 190, 40), firstLabel(panel).getForeground(),
            "Indicator should turn green after reconnect");
    }

    @Test
    @DisplayName("refresh() with null manager leaves label as 'Not connected'")
    void refresh_nullManagerStaysNotConnected() throws Exception {
        SwingUtilities.invokeAndWait(() -> panel.addDevice("HTZ", null));
        SwingUtilities.invokeAndWait(() -> panel.refresh());

        assertTrue(firstLabel(panel).getText().contains("Not connected"),
            "Null manager should always remain 'Not connected' after refresh()");
    }

    // =========================================================================
    //  ← Launcher button
    // =========================================================================

    @Test
    @DisplayName("'← Launcher' button is present in the panel")
    void launcherButton_isPresent() {
        assertNotNull(findButton(panel, "← Launcher"),
            "'← Launcher' button should be present in the panel");
    }

    @Test
    @DisplayName("'← Launcher' button is not focusable (does not steal keyboard focus)")
    void launcherButton_isNotFocusable() {
        JButton btn = findButton(panel, "← Launcher");
        assertNotNull(btn);
        assertFalse(btn.isFocusable(),
            "'← Launcher' button should not be focusable so it cannot steal keyboard shortcuts");
    }

    @Test
    @DisplayName("'← Launcher' button has a tooltip")
    void launcherButton_hasTooltip() {
        JButton btn = findButton(panel, "← Launcher");
        assertNotNull(btn);
        assertNotNull(btn.getToolTipText(),
            "'← Launcher' button should have a tooltip");
        assertFalse(btn.getToolTipText().isBlank(),
            "Tooltip text should not be blank");
    }

    @Test
    @DisplayName("'← Launcher' button disposes the containing JFrame when clicked")
    void launcherButton_disposesParentWindow() throws Exception {
        JFrame[] frame = {null};
        SwingUtilities.invokeAndWait(() -> {
            frame[0] = new JFrame("test-host");
            frame[0].add(panel);
            frame[0].pack(); // establishes native peer → isDisplayable() becomes true
        });

        assertTrue(frame[0].isDisplayable(),
            "Frame should be displayable before the button is clicked");

        JButton btn = findButton(panel, "← Launcher");
        assertNotNull(btn, "'← Launcher' button must be found before clicking");
        SwingUtilities.invokeAndWait(() -> btn.doClick());

        assertFalse(frame[0].isDisplayable(),
            "Parent JFrame should be disposed (non-displayable) after '← Launcher' is clicked");
    }

    // =========================================================================
    //  Component-tree helpers
    // =========================================================================

    /**
     * Returns the first {@link JLabel} found anywhere in the component subtree
     * rooted at {@code root}, or {@code null} if none exists.
     */
    private static JLabel firstLabel(Container root) {
        List<JLabel> all = allLabels(root);
        return all.isEmpty() ? null : all.get(0);
    }

    /** Collects every {@link JLabel} in the entire component subtree. */
    private static List<JLabel> allLabels(Container root) {
        List<JLabel> result = new ArrayList<>();
        collectLabels(root, result);
        return result;
    }

    private static void collectLabels(Container container, List<JLabel> out) {
        for (Component c : container.getComponents()) {
            if (c instanceof JLabel lbl)      out.add(lbl);
            if (c instanceof Container sub)   collectLabels(sub, out);
        }
    }

    /**
     * Searches the component subtree for a {@link JButton} whose text equals
     * {@code text}, returning {@code null} if not found.
     */
    private static JButton findButton(Container root, String text) {
        for (Component c : root.getComponents()) {
            if (c instanceof JButton btn && text.equals(btn.getText())) return btn;
            if (c instanceof Container sub) {
                JButton found = findButton(sub, text);
                if (found != null) return found;
            }
        }
        return null;
    }
}
