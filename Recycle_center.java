
#######################################################3

JAR FILES

MySQL
jcalendar
jcommon
jfreechart



##############################################


package recyclingcenter;

public interface Constants {
    int WIN_WIDTH  = 700;
    int WIN_HEIGHT = 400;
}


// DBConnection.java
package recyclingcenter;

import java.sql.*;
import java.time.LocalDate;

public class DBConnection {
    private static final String URL      = "jdbc:mysql://localhost:3306/";
    private static final String DB_NAME  = "recycling_db";
    private static final String USER     = "root";
    private static final String PASSWORD = "sabari3112";

    static { initialize(); }

    public static void initialize() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            // 1) Create database
            try (Connection tmp = DriverManager.getConnection(URL, USER, PASSWORD);
                 Statement st = tmp.createStatement()) {
                st.executeUpdate("CREATE DATABASE IF NOT EXISTS " + DB_NAME);
            }
            // 2) Create tables
            try (Connection conn = getConnection();
                 Statement st = conn.createStatement()) {

                st.executeUpdate("""
                  CREATE TABLE IF NOT EXISTS users (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    username VARCHAR(50) UNIQUE NOT NULL,
                    password VARCHAR(50) NOT NULL,
                    role VARCHAR(10) NOT NULL DEFAULT 'user'
                  )""");

                st.executeUpdate("""
                  CREATE TABLE IF NOT EXISTS inventory (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    material_type VARCHAR(50) UNIQUE NOT NULL,
                    quantity_kg DOUBLE DEFAULT 0,
                    price DOUBLE NOT NULL
                  )""");

                st.executeUpdate("""
                  CREATE TABLE IF NOT EXISTS staff (
                    staff_id INT AUTO_INCREMENT PRIMARY KEY,
                    name VARCHAR(100),
                    role VARCHAR(50)
                  )""");

                st.executeUpdate("""
                  CREATE TABLE IF NOT EXISTS vehicles (
                    vehicle_id INT AUTO_INCREMENT PRIMARY KEY,
                    vehicle_type VARCHAR(50),
                    driver_name VARCHAR(100),
                    waste_type VARCHAR(50)
                  )""");

                st.executeUpdate("""
                  CREATE TABLE IF NOT EXISTS pickups (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    name VARCHAR(100) NOT NULL,
                    addr VARCHAR(255) NOT NULL,
                    waste_type VARCHAR(50) NOT NULL,
                    driver VARCHAR(100) NOT NULL,
                    date DATE NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                  )""");

                st.executeUpdate("""
                  CREATE TABLE IF NOT EXISTS sorted_waste (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    type VARCHAR(50),
                    weight DOUBLE,
                    staff VARCHAR(100),
                    driver VARCHAR(100),
                    transport VARCHAR(50),
                    sorted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                  )""");

                st.executeUpdate("""
                  CREATE TABLE IF NOT EXISTS sales (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    sale_date DATE,
                    material_type VARCHAR(50),
                    quantity_kg DOUBLE,
                    amount DOUBLE
                  )""");
            }

            // 3) Seed admin user
            try (Connection c = getConnection();
                 PreparedStatement ps = c.prepareStatement(
                   "SELECT id FROM users WHERE username='admin'");
                 ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    try (PreparedStatement ins = c.prepareStatement(
                      "INSERT INTO users(username,password,role) VALUES('admin','admin123','admin')")) {
                        ins.executeUpdate();
                    }
                }
            }

            // 4) Seed inventory
            String[] wasteTypes = {"Plastic","Glass","Wood","Paper","Other"};
            double[] prices      = {10,8,5,4,6};
            try (Connection c = getConnection();
                 PreparedStatement ps = c.prepareStatement(
                   "INSERT IGNORE INTO inventory(material_type,price) VALUES(?,?)")) {
                for (int i = 0; i < wasteTypes.length; i++) {
                    ps.setString(1, wasteTypes[i]);
                    ps.setDouble(2, prices[i]);
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            // 5) Seed staff (2 per waste type, real names)
            String[][] staffNames = {
              {"Alice","Bob"},
              {"Charlie","David"},
              {"Eve","Frank"},
              {"Grace","Hannah"},
              {"Ivy","Jack"}
            };
            try (Connection c = getConnection()) {
                for (int i = 0; i < wasteTypes.length; i++) {
                    // count existing
                    try (PreparedStatement chk = c.prepareStatement(
                      "SELECT COUNT(*) FROM staff WHERE role=?")) {
                        chk.setString(1, wasteTypes[i]);
                        try (ResultSet rs = chk.executeQuery()) {
                            rs.next();
                            if (rs.getInt(1) < 2) {
                                try (PreparedStatement ins = c.prepareStatement(
                                  "INSERT INTO staff(name,role) VALUES(?,?)")) {
                                    ins.setString(1, staffNames[i][0]);
                                    ins.setString(2, wasteTypes[i]);
                                    ins.addBatch();
                                    ins.setString(1, staffNames[i][1]);
                                    ins.setString(2, wasteTypes[i]);
                                    ins.addBatch();
                                    ins.executeBatch();
                                }
                            }
                        }
                    }
                }
            }

            // 6) Seed vehicles: 2 drivers for each wasteType×vehicleType
            String[] vehicleTypes = {"Truck","Van","Bike","Car","Bus"};
            String[][] driverNames = {
              {"Olivia","Liam","Mason","Sophia","Ella"},
              {"Noah","Emma","Ava","Lucas","Ethan"}
            };
            try (Connection c = getConnection()) {
                for (String wt : wasteTypes) {
                    for (int v = 0; v < vehicleTypes.length; v++) {
                        // check count
                        try (PreparedStatement chk = c.prepareStatement(
                          "SELECT COUNT(*) FROM vehicles WHERE waste_type=? AND vehicle_type=?")) {
                            chk.setString(1, wt);
                            chk.setString(2, vehicleTypes[v]);
                            try (ResultSet rs = chk.executeQuery()) {
                                rs.next();
                                if (rs.getInt(1) < 2) {
                                    try (PreparedStatement ins = c.prepareStatement(
                                      "INSERT INTO vehicles(vehicle_type,driver_name,waste_type) VALUES(?,?,?)")) {
                                        ins.setString(1, vehicleTypes[v]);
                                        ins.setString(2, driverNames[0][v]);
                                        ins.setString(3, wt);
                                        ins.addBatch();
                                        ins.setString(2, driverNames[1][v]);
                                        ins.addBatch();
                                        ins.executeBatch();
                                    }
                                }
                            }
                        }
                    }
                }
            }

            System.out.println("✔ Database & seed data ready");
        } catch (Exception e) {
            throw new RuntimeException("DB initialization failed", e);
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL + DB_NAME, USER, PASSWORD);
    }
}


// DashboardWindow.java
package recyclingcenter;

import javax.swing.*;
import java.awt.*;

public class DashboardWindow extends JFrame implements Constants {
    public DashboardWindow() {
        super("♻ Dashboard");
        setSize(WIN_WIDTH, WIN_HEIGHT);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        String[] labels = {
          "Pickup Requests","Waste Sorting","Inventory",
          "Sales Tracker","Reports","Vehicles",
          "Staff Management","Manage Pickups","Logout"
        };
        Color[] colors = {
          new Color(244,67,54), new Color(33,150,243), new Color(76,175,80),
          new Color(255,193,7), new Color(156,39,176), new Color(0,188,212),
          new Color(121,85,72), new Color(63,81,181), new Color(96,125,139)
        };

        JPanel panel = new JPanel(new GridLayout(3,3,15,15));
        panel.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));

        for (int i = 0; i < labels.length; i++) {
            String txt = labels[i];
            JButton b = new JButton(txt);
            b.setFont(new Font("Segoe UI", Font.BOLD, 16));
            b.setBackground(colors[i]);
            b.setForeground(Color.WHITE);
            panel.add(b);
            b.addActionListener(e -> {
                dispose();
                switch (txt) {
                  case "Pickup Requests":    new PickupRequestWindow().setVisible(true); break;
                  case "Waste Sorting":      new WasteSortingWindow().setVisible(true);    break;
                  case "Inventory":          new InventoryManagementWindow().setVisible(true); break;
                  case "Sales Tracker":      new SalesTrackerWindow().setVisible(true);   break;
                  case "Reports":            new ReportsWindow().setVisible(true);        break;
                  case "Vehicles":           new VehicleManagementWindow().setVisible(true); break;
                  case "Staff Management":   new StaffManagementWindow().setVisible(true);  break;
                  case "Manage Pickups":     new ManagePickupsWindow().setVisible(true);    break;
                  case "Logout":
                    Session.username = null; Session.role = null;
                    new LoginWindow().setVisible(true);
                }
            });
        }

        add(panel);
    }
}

// InventoryManagementWindow.java
package recyclingcenter;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.sql.*;

public class InventoryManagementWindow extends JFrame implements Constants {
    private final JTextField materialField = new JTextField(10);
    private final JTextField priceField    = new JTextField(6);
    private final DefaultTableModel model  = new DefaultTableModel(
        new String[]{"Material","Quantity(kg)","Price/kg"},0
    );
    private final JTable table = new JTable(model);

    public InventoryManagementWindow() {
        super("📦 Inventory Management");
        setSize(WIN_WIDTH,WIN_HEIGHT);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel top = new JPanel();
        top.setBackground(new Color(232,234,246));
        top.add(new JLabel("Material:")); top.add(materialField);
        top.add(new JLabel("Price/kg:")); top.add(priceField);
        JButton addBtn = new JButton("Add/Update");
        addBtn.setBackground(new Color(63,81,181)); addBtn.setForeground(Color.WHITE);
        top.add(addBtn);

        table.setFillsViewportHeight(true);
        table.setDefaultRenderer(Object.class,new DefaultTableCellRenderer(){
            public Component getTableCellRendererComponent(JTable t,Object v,
               boolean isSel,boolean hasFocus,int r,int c){
                super.getTableCellRendererComponent(t,v,isSel,hasFocus,r,c);
                setBackground(r%2==0?Color.WHITE:new Color(224,224,224));
                return this;
            }
        });

        JButton back = new JButton("Back");
        back.setBackground(new Color(189,189,189));
        back.addActionListener(e -> { new DashboardWindow().setVisible(true); dispose(); });

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(back, BorderLayout.SOUTH);

        loadData();
        addBtn.addActionListener(e -> upsertMaterial());
    }

    private void loadData(){
        model.setRowCount(0);
        try (Connection c = DBConnection.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                "SELECT material_type,quantity_kg,price FROM inventory")) {
            while(rs.next()){
                model.addRow(new Object[]{
                  rs.getString(1), rs.getDouble(2), rs.getDouble(3)
                });
            }
        } catch(SQLException e){ e.printStackTrace(); }
    }

    private void upsertMaterial(){
        String mat = materialField.getText().trim();
        String pr  = priceField.getText().trim();
        if(mat.isEmpty()||pr.isEmpty()) return;
        try (Connection c = DBConnection.getConnection()){
            PreparedStatement ps = c.prepareStatement(
              "UPDATE inventory SET price=? WHERE material_type=?");
            ps.setDouble(1,Double.parseDouble(pr));
            ps.setString (2,mat);
            int rows = ps.executeUpdate();
            if(rows==0){
                ps = c.prepareStatement(
                  "INSERT INTO inventory(material_type,price) VALUES(?,?)");
                ps.setString(1,mat);
                ps.setDouble(2,Double.parseDouble(pr));
                ps.executeUpdate();
            }
            materialField.setText(""); priceField.setText("");
            loadData();
        } catch(Exception e){ e.printStackTrace(); }
    }
}

// LoginWindow.java
package recyclingcenter;

import javax.swing.*;
import java.awt.*;
import java.sql.*;

public class LoginWindow extends JFrame implements Constants {
    private final JTextField userField = new JTextField(15);
    private final JPasswordField passField = new JPasswordField(15);

    public LoginWindow() {
        super("♻ Recycling Center Login");
        setSize(WIN_WIDTH, WIN_HEIGHT);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(new Color(200,230,201));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(10,10,10,10);

        JLabel title = new JLabel("Admin/Staff Login");
        title.setFont(new Font("Segoe UI", Font.BOLD, 28));
        title.setForeground(new Color(56,142,60));
        c.gridwidth=2; p.add(title,c);

        c.gridwidth=1;
        c.gridy=1; p.add(new JLabel("Username:"),c);
        c.gridx=1; p.add(userField,c);

        c.gridy=2; c.gridx=0; p.add(new JLabel("Password:"),c);
        c.gridx=1; p.add(passField,c);

        JButton loginBtn = new JButton("Login");
        loginBtn.setBackground(new Color(67,160,71));
        loginBtn.setForeground(Color.WHITE);
        loginBtn.setFont(new Font("Segoe UI", Font.BOLD, 16));
        c.gridwidth=2; c.gridy=3; p.add(loginBtn,c);

        JButton regBtn = new JButton("Register");
        regBtn.setBackground(new Color(100,181,246));
        regBtn.setForeground(Color.WHITE);
        regBtn.setFont(new Font("Segoe UI", Font.BOLD, 16));
        c.gridy=4; p.add(regBtn,c);

        loginBtn.addActionListener(e -> doLogin());
        regBtn.addActionListener(e -> {
            new RegisterWindow().setVisible(true);
            dispose();
        });

        add(p);
    }

    private void doLogin() {
        String u = userField.getText().trim();
        String p = new String(passField.getPassword());
        if (u.isEmpty() || p.isEmpty()) {
            JOptionPane.showMessageDialog(this,"Enter both fields","Error",JOptionPane.ERROR_MESSAGE);
            return;
        }
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
               "SELECT role FROM users WHERE username=? AND password=?")) {
            ps.setString(1,u); ps.setString(2,p);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Session.username = u;
                    Session.role     = rs.getString("role");
                    new DashboardWindow().setVisible(true);
                    dispose();
                } else {
                    JOptionPane.showMessageDialog(this,"Invalid credentials","Error",JOptionPane.ERROR_MESSAGE);
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void main(String[] args) {
        DBConnection.initialize();
        SwingUtilities.invokeLater(() -> new LoginWindow().setVisible(true));
    }
}


// ManagePickupsWindow.java
package recyclingcenter;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.sql.*;

public class ManagePickupsWindow extends JFrame implements Constants {
    private final DefaultTableModel model = new DefaultTableModel(
        new String[]{"ID","Name","Addr","Waste","Driver","Date"},0);
    private final JTable table = new JTable(model);

    public ManagePickupsWindow() {
        super("🗑 Manage Pickups");
        setSize(WIN_WIDTH, WIN_HEIGHT);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        getContentPane().setBackground(new Color(224,242,241));

        loadData();
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane scroll = new JScrollPane(table);

        JButton deleteBtn = new JButton("Delete");
        deleteBtn.setEnabled("admin".equals(Session.role));
        deleteBtn.setBackground(new Color(244,67,54));
        deleteBtn.setForeground(Color.WHITE);
        deleteBtn.addActionListener(e -> deleteSelected());

        JButton back = new JButton("Back");
        back.setBackground(new Color(189,189,189));
        back.addActionListener(e -> {
            new DashboardWindow().setVisible(true);
            dispose();
        });

        JPanel south = new JPanel();
        south.setOpaque(false);
        south.add(deleteBtn);
        south.add(back);

        add(scroll, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
    }

    private void loadData() {
        model.setRowCount(0);
        try (Connection c = DBConnection.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
               "SELECT id,name,addr,waste_type,driver,date FROM pickups")) {
            while (rs.next()) {
                model.addRow(new Object[]{
                  rs.getInt(1),
                  rs.getString(2),
                  rs.getString(3),
                  rs.getString(4),
                  rs.getString(5),
                  rs.getDate(6).toString()
                });
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void deleteSelected() {
        int r = table.getSelectedRow();
        if (r<0) return;
        int id = (int) model.getValueAt(r,0);
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
               "DELETE FROM pickups WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
            loadData();
        } catch (SQLException e) { e.printStackTrace(); }
    }
}


// PickupRequestWindow.java
package recyclingcenter;

import javax.swing.*;
import com.toedter.calendar.JDateChooser;
import java.awt.*;
import java.sql.*;
import java.util.regex.*;

public class PickupRequestWindow extends JFrame implements Constants {
    private final JTextField nameField   = new JTextField(12);
    private final JTextField addrField   = new JTextField(12);
    private final JComboBox<String> typeBox   = new JComboBox<>();
    private final JComboBox<String> driverBox = new JComboBox<>();
    private final JDateChooser dateChooser    = new JDateChooser();

    public PickupRequestWindow() {
        super("🚛 Pickup Requests");
        setSize(WIN_WIDTH, WIN_HEIGHT);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        // load waste types
        try (Connection c = DBConnection.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT material_type FROM inventory")) {
            while (rs.next()) typeBox.addItem(rs.getString(1));
        } catch (SQLException e) { e.printStackTrace(); }

        typeBox.addActionListener(e -> loadDrivers());
        dateChooser.setDateFormatString("yyyy-MM-dd");

        JPanel p = new JPanel(new GridLayout(6,2,12,12));
        p.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));
        p.setBackground(new Color(255,243,224));

        p.add(new JLabel("Name:"));        p.add(nameField);
        p.add(new JLabel("Address:"));     p.add(addrField);
        p.add(new JLabel("Waste Type:"));  p.add(typeBox);
        p.add(new JLabel("Driver:"));      p.add(driverBox);
        p.add(new JLabel("Pickup Date:")); p.add(dateChooser);

        JButton submit = new JButton("Submit");
        submit.setBackground(new Color(255,167,38));
        submit.setForeground(Color.WHITE);
        JButton back = new JButton("Back");
        back.setBackground(new Color(189,189,189));

        p.add(submit); p.add(back);

        submit.addActionListener(e -> save());
        back.addActionListener(e -> {
            new DashboardWindow().setVisible(true);
            dispose();
        });

        add(p);
        loadDrivers();
    }

    private void loadDrivers() {
        driverBox.removeAllItems();
        String wt = (String) typeBox.getSelectedItem();
        if (wt == null) return;
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
               "SELECT driver_name FROM vehicles WHERE waste_type=?")) {
            ps.setString(1, wt);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) driverBox.addItem(rs.getString(1));
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void save() {
        String name = nameField.getText().trim();
        String addr = addrField.getText().trim();
        String wt   = (String) typeBox.getSelectedItem();
        String drv  = (String) driverBox.getSelectedItem();
        java.util.Date dt = dateChooser.getDate();

        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this,"Enter name","Error",JOptionPane.ERROR_MESSAGE);
            return;
        }
        // address: ≥5 chars, alnum + space , . -
        if (!Pattern.matches("^[A-Za-z0-9 ,.-]{5,}$", addr)) {
            JOptionPane.showMessageDialog(this,"Enter valid address","Error",JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (wt==null) {
            JOptionPane.showMessageDialog(this,"Select waste type","Error",JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (drv==null) {
            JOptionPane.showMessageDialog(this,"Select driver","Error",JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (dt==null) {
            JOptionPane.showMessageDialog(this,"Pick a date","Error",JOptionPane.ERROR_MESSAGE);
            return;
        }

        String sql = """
          INSERT INTO pickups(name,addr,waste_type,driver,date)
          VALUES(?,?,?,?,?)
        """;
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, addr);
            ps.setString(3, wt);
            ps.setString(4, drv);
            ps.setDate(5, new java.sql.Date(dt.getTime()));
            ps.executeUpdate();
            JOptionPane.showMessageDialog(this,"Pickup scheduled!","Success",JOptionPane.INFORMATION_MESSAGE);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,"DB Error: "+ex.getMessage(),"Error",JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }
}



// RegisterWindow.java
package recyclingcenter;

import javax.swing.*;
import java.awt.*;
import java.sql.*;
import java.util.regex.*;

public class RegisterWindow extends JFrame implements Constants {
    private final JTextField userField   = new JTextField(15);
    private final JPasswordField passField  = new JPasswordField(15);
    private final JPasswordField pass2Field = new JPasswordField(15);

    public RegisterWindow() {
        super("📋 User Registration");
        setSize(WIN_WIDTH, WIN_HEIGHT);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(new Color(255,224,178));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(10,10,10,10);

        JLabel title = new JLabel("Create Account");
        title.setFont(new Font("Segoe UI", Font.BOLD, 24));
        title.setForeground(new Color(255,111,0));
        c.gridwidth=2; p.add(title,c);

        c.gridwidth=1;
        c.gridy=1; p.add(new JLabel("Username:"),c);
        c.gridx=1; p.add(userField,c);

        c.gridy=2; c.gridx=0; p.add(new JLabel("Password:"),c);
        c.gridx=1; p.add(passField,c);

        c.gridy=3; c.gridx=0; p.add(new JLabel("Confirm:"),c);
        c.gridx=1; p.add(pass2Field,c);

        JButton regBtn = new JButton("Register");
        regBtn.setBackground(new Color(255,167,38));
        regBtn.setForeground(Color.WHITE);
        regBtn.setFont(new Font("Segoe UI", Font.BOLD, 16));
        c.gridwidth=2; c.gridy=4; p.add(regBtn,c);

        JButton backBtn = new JButton("Back");
        backBtn.setBackground(new Color(189,189,189));
        c.gridy=5; p.add(backBtn,c);

        regBtn.addActionListener(e -> doRegister());
        backBtn.addActionListener(e -> {
            new LoginWindow().setVisible(true);
            dispose();
        });

        add(p);
    }

    private void doRegister() {
        String u  = userField.getText().trim();
        String p1 = new String(passField.getPassword());
        String p2 = new String(pass2Field.getPassword());

        if (u.isEmpty()||p1.isEmpty()||p2.isEmpty()) {
            JOptionPane.showMessageDialog(this,"Fill all fields","Error",JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (u.contains(" ")) {
            JOptionPane.showMessageDialog(this,"Username cannot contain spaces","Error",JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (!p1.equals(p2)) {
            JOptionPane.showMessageDialog(this,"Passwords do not match","Error",JOptionPane.ERROR_MESSAGE);
            return;
        }
        // ≥8 chars, upper, lower, digit, special
        Pattern pat = Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$");
        if (!pat.matcher(p1).matches()) {
            JOptionPane.showMessageDialog(this,
              "<html>Password must be ≥8 chars,<br>include uppercase, lowercase,<br>digit & special char</html>",
              "Error",JOptionPane.ERROR_MESSAGE);
            return;
        }

        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
               "INSERT INTO users(username,password,role) VALUES(?,?,'user')")) {
            ps.setString(1,u);
            ps.setString(2,p1);
            ps.executeUpdate();
            JOptionPane.showMessageDialog(this,
              "Registered! Please log in.","Success",JOptionPane.INFORMATION_MESSAGE);
            new LoginWindow().setVisible(true);
            dispose();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
              "User exists or DB error","Error",JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }
}


// ReportsWindow.java
package recyclingcenter;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.sql.*;
import org.jfree.chart.*;
import org.jfree.chart.plot.*;
import org.jfree.data.category.*;

public class ReportsWindow extends JFrame implements Constants {
    private final DefaultTableModel model = new DefaultTableModel(
        new String[]{"Metric","Value"},0);
    private final JTable table = new JTable(model);

    public ReportsWindow() {
        super("📊 Reports");
        setSize(WIN_WIDTH, WIN_HEIGHT);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        // table
        table.setFillsViewportHeight(true);
        table.setRowHeight(25);
        table.setDefaultRenderer(Object.class,new DefaultTableCellRenderer(){
            public Component getTableCellRendererComponent(JTable t,Object v,
               boolean isSel,boolean hasFocus,int r,int c){
                super.getTableCellRendererComponent(t,v,isSel,hasFocus,r,c);
                setBackground(r%2==0?new Color(227,242,253):new Color(187,222,251));
                setFont(new Font("Segoe UI",Font.PLAIN,16));
                return this;
            }
        });
        loadData();

        // chart dataset
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        try (Connection c = DBConnection.getConnection();
             Statement st = c.createStatement()) {
            ResultSet r1 = st.executeQuery("SELECT COUNT(*) FROM pickups"); r1.next();
            dataset.addValue(r1.getInt(1),"Count","Pickups");
            ResultSet r2 = st.executeQuery("SELECT SUM(weight) FROM sorted_waste"); r2.next();
            dataset.addValue(r2.getDouble(1),"Kgs","Sorted");
            ResultSet r3 = st.executeQuery("SELECT SUM(amount) FROM sales"); r3.next();
            dataset.addValue(r3.getDouble(1),"₹","Revenue");
        } catch (SQLException e) { e.printStackTrace(); }

        JFreeChart chart = ChartFactory.createBarChart(
          "Key Metrics","Metric","Value",dataset,
          PlotOrientation.VERTICAL,false,true,false
        );
        ChartPanel chartPanel = new ChartPanel(chart);
        chartPanel.setPreferredSize(new Dimension(300,300));

        JPanel center = new JPanel(new GridLayout(1,2));
        center.add(new JScrollPane(table));
        center.add(chartPanel);

        JButton back = new JButton("Back");
        back.addActionListener(e -> {
            new DashboardWindow().setVisible(true);
            dispose();
        });

        add(center,BorderLayout.CENTER);
        add(back,BorderLayout.SOUTH);
    }

    private void loadData() {
        model.setRowCount(0);
        try (Connection c = DBConnection.getConnection();
             Statement st = c.createStatement()) {
            ResultSet r1 = st.executeQuery("SELECT COUNT(*) FROM pickups"); r1.next();
            model.addRow(new Object[]{"Total Pickups",r1.getInt(1)});
            ResultSet r2 = st.executeQuery("SELECT SUM(weight) FROM sorted_waste"); r2.next();
            model.addRow(new Object[]{"Total Waste Sorted (kg)",r2.getDouble(1)});
            ResultSet r3 = st.executeQuery("SELECT SUM(amount) FROM sales"); r3.next();
            model.addRow(new Object[]{"Total Sales Revenue (₹)",r3.getDouble(1)});
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}



// SalesTrackerWindow.java
package recyclingcenter;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.sql.*;

public class SalesTrackerWindow extends JFrame implements Constants {
    private final DefaultTableModel model = new DefaultTableModel(
        new String[]{"Date","Material","Qty(kg)","Amt(₹)"},0);
    private final JTable table = new JTable(model);

    public SalesTrackerWindow() {
        super("💰 Sales Tracker");
        setSize(WIN_WIDTH, WIN_HEIGHT);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JTableHeader header = table.getTableHeader();
        header.setBackground(new Color(30,136,229));
        header.setForeground(Color.WHITE);
        header.setFont(new Font("Segoe UI", Font.BOLD, 16));

        table.setDefaultRenderer(Object.class,new DefaultTableCellRenderer(){
            public Component getTableCellRendererComponent(JTable t,Object v,
               boolean isSel,boolean hasFocus,int r,int c){
                super.getTableCellRendererComponent(t,v,isSel,hasFocus,r,c);
                setBackground(r%2==0?Color.WHITE:new Color(240,248,255));
                return this;
            }
        });

        loadData();

        JButton back = new JButton("Back");
        back.setBackground(new Color(244,67,54)); back.setForeground(Color.WHITE);
        back.addActionListener(e -> { new DashboardWindow().setVisible(true); dispose(); });

        add(new JScrollPane(table), BorderLayout.CENTER);
        add(back, BorderLayout.SOUTH);
    }

    private void loadData() {
        model.setRowCount(0);
        try (Connection c = DBConnection.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
               "SELECT sale_date,material_type,quantity_kg,amount FROM sales")) {
            while (rs.next()) {
                model.addRow(new Object[]{
                  rs.getDate(1).toString(),
                  rs.getString(2),
                  rs.getDouble(3),
                  rs.getDouble(4)
                });
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}



package recyclingcenter;

public class Session {
    public static String username = null;
    public static String role     = null;   // “admin” or “user”
}


// StaffManagementWindow.java
package recyclingcenter;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.sql.*;

public class StaffManagementWindow extends JFrame implements Constants {
    private final JTextField nameField      = new JTextField(10);
    private final JComboBox<String> roleBox = new JComboBox<>();
    private final DefaultTableModel model   = new DefaultTableModel(
        new String[]{"ID","Name","Role"},0);
    private final JTable table              = new JTable(model);

    public StaffManagementWindow() {
        super("👥 Staff Management");
        setSize(WIN_WIDTH, WIN_HEIGHT);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel top = new JPanel();
        top.setBackground(new Color(232,245,233));
        try (Connection c = DBConnection.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT material_type FROM inventory")) {
            while (rs.next()) roleBox.addItem(rs.getString(1));
        } catch (SQLException e) { e.printStackTrace(); }

        top.add(new JLabel("Name:")); top.add(nameField);
        top.add(new JLabel("Role:")); top.add(roleBox);
        JButton addBtn = new JButton("Add");
        addBtn.setBackground(new Color(121,85,72));
        addBtn.setForeground(Color.WHITE);
        addBtn.addActionListener(e -> addStaff());
        top.add(addBtn);

        table.setFillsViewportHeight(true);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer(){
            public Component getTableCellRendererComponent(JTable t,Object v,
               boolean isSel,boolean hasFocus,int r,int c){
                super.getTableCellRendererComponent(t,v,isSel,hasFocus,r,c);
                setBackground(r%2==0?Color.WHITE:new Color(237,231,246));
                return this;
            }
        });

        JButton deleteBtn = new JButton("Delete");
        deleteBtn.setEnabled("admin".equals(Session.role));
        deleteBtn.setBackground(new Color(244,67,54));
        deleteBtn.setForeground(Color.WHITE);
        deleteBtn.addActionListener(e -> deleteSelected());

        JButton back = new JButton("Back");
        back.setBackground(new Color(189,189,189));
        back.addActionListener(e -> {
            new DashboardWindow().setVisible(true);
            dispose();
        });

        JPanel south = new JPanel();
        south.add(deleteBtn);
        south.add(back);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        loadData();
    }

    private void loadData() {
        model.setRowCount(0);
        try (Connection c = DBConnection.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
               "SELECT staff_id,name,role FROM staff")) {
            while (rs.next()) {
                model.addRow(new Object[]{
                  rs.getInt(1), rs.getString(2), rs.getString(3)
                });
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void addStaff() {
        String name = nameField.getText().trim();
        String role = (String) roleBox.getSelectedItem();
        if (name.isEmpty()) return;
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
               "INSERT INTO staff(name,role) VALUES(?,?)")) {
            ps.setString(1,name);
            ps.setString(2,role);
            ps.executeUpdate();
            nameField.setText("");
            loadData();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void deleteSelected() {
        int r = table.getSelectedRow();
        if (r<0) return;
        int id = (int) model.getValueAt(r,0);
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
               "DELETE FROM staff WHERE staff_id=?")) {
            ps.setInt(1,id);
            ps.executeUpdate();
            loadData();
        } catch (SQLException e) { e.printStackTrace(); }
    }
}




// VehicleManagementWindow.java
package recyclingcenter;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.sql.*;

public class VehicleManagementWindow extends JFrame implements Constants {
    private final JComboBox<String> transportBox = new JComboBox<>(
        new String[]{"Truck","Van","Bike","Car","Bus"});
    private final JTextField driverField       = new JTextField(10);
    private final JComboBox<String> wasteBox   = new JComboBox<>();
    private final DefaultTableModel model      = new DefaultTableModel(
        new String[]{"ID","Transport","Driver","Waste Type"},0);
    private final JTable table                 = new JTable(model);

    public VehicleManagementWindow(){
        super("🚚 Vehicle Management");
        setSize(WIN_WIDTH, WIN_HEIGHT);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel top = new JPanel();
        top.setBackground(new Color(232,240,254));
        // load waste types
        try (Connection c = DBConnection.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT material_type FROM inventory")) {
            while (rs.next()) wasteBox.addItem(rs.getString(1));
        } catch (SQLException e) { e.printStackTrace(); }

        top.add(new JLabel("Transport:")); top.add(transportBox);
        top.add(new JLabel("Driver:"));    top.add(driverField);
        top.add(new JLabel("Waste Type:"));top.add(wasteBox);
        JButton add = new JButton("Add");
        add.setBackground(new Color(0,150,136));
        add.setForeground(Color.WHITE);
        add.addActionListener(e -> addVehicle());
        top.add(add);

        table.setFillsViewportHeight(true);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer(){
            public Component getTableCellRendererComponent(JTable t,Object v,
               boolean isSel,boolean hasFocus,int r,int c){
                super.getTableCellRendererComponent(t,v,isSel,hasFocus,r,c);
                setBackground(r%2==0?Color.WHITE:new Color(225,245,254));
                return this;
            }
        });

        JButton deleteBtn = new JButton("Delete");
        deleteBtn.setEnabled("admin".equals(Session.role));
        deleteBtn.setBackground(new Color(244,67,54));
        deleteBtn.setForeground(Color.WHITE);
        deleteBtn.addActionListener(e -> deleteSelected());

        JButton back = new JButton("Back");
        back.setBackground(new Color(189,189,189));
        back.addActionListener(e -> {
            new DashboardWindow().setVisible(true);
            dispose();
        });

        JPanel south = new JPanel();
        south.add(deleteBtn);
        south.add(back);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        loadData();
    }

    private void loadData(){
        model.setRowCount(0);
        try (Connection c = DBConnection.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
               "SELECT vehicle_id,vehicle_type,driver_name,waste_type FROM vehicles")) {
            while (rs.next()) {
                model.addRow(new Object[]{
                  rs.getInt(1), rs.getString(2),
                  rs.getString(3), rs.getString(4)
                });
            }
        } catch (SQLException e){ e.printStackTrace(); }
    }

    private void addVehicle(){
        String trans = (String) transportBox.getSelectedItem();
        String drv   = driverField.getText().trim();
        String waste = (String) wasteBox.getSelectedItem();
        if (drv.isEmpty()) return;
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
               "INSERT INTO vehicles(vehicle_type,driver_name,waste_type) VALUES(?,?,?)")) {
            ps.setString(1, trans);
            ps.setString(2, drv);
            ps.setString(3, waste);
            ps.executeUpdate();
            driverField.setText("");
            loadData();
        } catch (SQLException e){ e.printStackTrace(); }
    }

    private void deleteSelected(){
        int r = table.getSelectedRow();
        if (r<0) return;
        int id = (int) model.getValueAt(r,0);
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
               "DELETE FROM vehicles WHERE vehicle_id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
            loadData();
        } catch (SQLException e){ e.printStackTrace(); }
    }
}



// WasteSortingWindow.java
package recyclingcenter;

import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.sql.*;
import java.util.*;

public class WasteSortingWindow extends JFrame implements Constants {
    private final JComboBox<String> typeBox      = new JComboBox<>();
    private final JTextField         weightField = new JTextField();
    private final JComboBox<String> staffBox     = new JComboBox<>();
    private final JComboBox<String> transportBox = new JComboBox<>(
        new String[]{"Truck","Van","Bike","Car","Bus"});
    private final JComboBox<String> driverBox    = new JComboBox<>();
    private final JLabel             amountLabel = new JLabel("₹0.00");
    private final Map<String,Double> priceMap    = new HashMap<>();

    public WasteSortingWindow() {
        super("♻ Waste Sorting");
        setSize(WIN_WIDTH, WIN_HEIGHT);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        reloadTypes(); reloadPrices();
        reloadStaff(typeBox.getSelectedItem());
        reloadDrivers(typeBox.getSelectedItem(), transportBox.getSelectedItem());

        JPanel p = new JPanel(new GridLayout(7,2,10,10));
        p.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));
        p.setBackground(new Color(232,245,233));

        p.add(new JLabel("Waste Type:"));     p.add(typeBox);
        p.add(new JLabel("Weight (kg):"));    p.add(weightField);
        p.add(new JLabel("Assign Staff:"));   p.add(staffBox);
        p.add(new JLabel("Transport Type:")); p.add(transportBox);
        p.add(new JLabel("Assign Driver:"));  p.add(driverBox);
        p.add(new JLabel("Amount:"));         p.add(amountLabel);

        JButton saveBtn = new JButton("Save");
        saveBtn.setBackground(new Color(76,175,80)); saveBtn.setForeground(Color.WHITE);
        p.add(saveBtn);

        JButton backBtn = new JButton("Back");
        backBtn.setBackground(new Color(189,189,189));
        p.add(backBtn);

        typeBox.addActionListener(e -> {
            updateAmount();
            reloadStaff(typeBox.getSelectedItem());
            reloadDrivers(typeBox.getSelectedItem(), transportBox.getSelectedItem());
        });
        transportBox.addActionListener(e ->
            reloadDrivers(typeBox.getSelectedItem(), transportBox.getSelectedItem())
        );
        weightField.getDocument().addDocumentListener(new DocumentListener(){
            public void insertUpdate(DocumentEvent e){ updateAmount(); }
            public void removeUpdate(DocumentEvent e){ updateAmount(); }
            public void changedUpdate(DocumentEvent e){ updateAmount(); }
        });

        saveBtn.addActionListener(e -> saveEntry());
        backBtn.addActionListener(e -> {
            new DashboardWindow().setVisible(true);
            dispose();
        });

        add(p);
    }

    private void reloadTypes() {
        typeBox.removeAllItems();
        try (Connection c = DBConnection.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT material_type FROM inventory")) {
            while (rs.next()) typeBox.addItem(rs.getString(1));
        } catch (SQLException ex) { ex.printStackTrace(); }
    }

    private void reloadPrices() {
        priceMap.clear();
        try (Connection c = DBConnection.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
               "SELECT material_type, price FROM inventory")) {
            while (rs.next()) priceMap.put(rs.getString(1), rs.getDouble(2));
        } catch (SQLException ex) { ex.printStackTrace(); }
    }

    private void reloadStaff(Object selectedType) {
        staffBox.removeAllItems();
        if (selectedType==null) return;
        String type = selectedType.toString();
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
               "SELECT name FROM staff WHERE role = ?")) {
            ps.setString(1,type);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) staffBox.addItem(rs.getString("name"));
            }
        } catch (SQLException ex) { ex.printStackTrace(); }
    }

    private void reloadDrivers(Object selectedType, Object selectedTransport) {
        driverBox.removeAllItems();
        if (selectedType==null||selectedTransport==null) return;
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
               "SELECT driver_name FROM vehicles WHERE waste_type=? AND vehicle_type=?")) {
            ps.setString(1, selectedType.toString());
            ps.setString(2, selectedTransport.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) driverBox.addItem(rs.getString("driver_name"));
            }
        } catch (SQLException ex) { ex.printStackTrace(); }
    }

    private void updateAmount() {
        try {
            double wt   = Double.parseDouble(weightField.getText().trim());
            if (wt <= 0) throw new NumberFormatException();
            String type = (String) typeBox.getSelectedItem();
            double price= priceMap.getOrDefault(type,0.0);
            amountLabel.setText(String.format("₹%.2f", price * wt));
        } catch (Exception e) {
            amountLabel.setText("₹0.00");
        }
    }

    private void saveEntry() {
        String type      = (String) typeBox.getSelectedItem();
        String staff     = (String) staffBox.getSelectedItem();
        String transport = (String) transportBox.getSelectedItem();
        String driver    = (String) driverBox.getSelectedItem();
        double wt;
        try {
            wt = Double.parseDouble(weightField.getText().trim());
            if (wt <= 0) throw new NumberFormatException();
        } catch (NumberFormatException ne) {
            JOptionPane.showMessageDialog(this,"Enter valid positive weight","Error",JOptionPane.ERROR_MESSAGE);
            return;
        }

        double price = priceMap.getOrDefault(type,0.0);
        double amt   = price * wt;

        try (Connection c = DBConnection.getConnection()) {
            try (PreparedStatement ps1 = c.prepareStatement(
              "INSERT INTO sorted_waste(type,weight,staff,driver,transport) VALUES(?,?,?,?,?)")) {
                ps1.setString(1,type);
                ps1.setDouble(2,wt);
                ps1.setString(3,staff);
                ps1.setString(4,driver);
                ps1.setString(5,transport);
                ps1.executeUpdate();
            }
            try (PreparedStatement ps2 = c.prepareStatement(
              "INSERT INTO sales(sale_date,material_type,quantity_kg,amount) VALUES(CURDATE(),?,?,?)")) {
                ps2.setString(1,type);
                ps2.setDouble(2,wt);
                ps2.setDouble(3,amt);
                ps2.executeUpdate();
            }
            try (PreparedStatement ps3 = c.prepareStatement(
              "UPDATE inventory SET quantity_kg = quantity_kg + ? WHERE material_type = ?")) {
                ps3.setDouble(1,wt);
                ps3.setString(2,type);
                ps3.executeUpdate();
            }
        } catch (SQLException se) {
            JOptionPane.showMessageDialog(this,"DB Error: "+se.getMessage(),"Error",JOptionPane.ERROR_MESSAGE);
            se.printStackTrace();
            return;
        }

        JOptionPane.showMessageDialog(this,
          String.format("Saved! ₹%.2f", amt),"Success",JOptionPane.INFORMATION_MESSAGE);
        weightField.setText("");
        amountLabel.setText("₹0.00");
    }
}



