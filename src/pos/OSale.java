/*
    Point of Sale System Project
    Authors: Clayton Barber, Brandon Barton, Declan Brennan, Maximilian Hasselbusch, Eric Metcalf
    Last Updated: 20 November 2015
 */
package pos;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.sql.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OSale {

    //This will be a linked list of items
    private PrintStream ps;
    OLinkedListOfItems l;
    Money total = new Money(0);
    Money subtotal = new Money(0);
    Money tax = new Money(0);
    private ArrayList<OItem> items;

    //This will create the sales class
    public OSale(PrintStream ps) {
        l = new OLinkedListOfItems();
        this.ps = ps;
        items = new ArrayList<OItem>();
        fillOItems();
    }

    //This function adds the Item to the class
    public void addItem(int sku, int quant, boolean rental) {
        Money price;
        price = checkSKU(sku);
        if (price != null) {
            OItem it = new OItem(sku, price);
            l.add(it, quant, rental);
            subtotal.add(subtotal.getValue(), (quant * Money.toDouble(it.getPrice())));
        } else {
            System.out.println("Invalid SKU added nothing");

        }
    }

    void removeItem(int iid, boolean rental) {
        Money price;
        price = checkSKU(iid);
        if (price != null) {
            OItem it = new OItem(iid, price);
            if (l.find(it, rental)) {
                int q = l.remove(it, rental);
                subtotal.subtract(subtotal.getValue(), (q * Money.toDouble(it.getPrice())));
            } else {
                System.out.println("Invalid SKU, removed nothing");
            }
        } else {
            System.out.println("Invalid SKU, removed nothing");
        }
    }

    public void commitSale(String ccn) {
        String tid = null;
        String retid = null;
        ArrayList<String> old = new ArrayList<String>();
        File f = new File("towrite.txt");
        try {
            if (f.exists()) {
                FileInputStream fstream = new FileInputStream(f);
                BufferedReader in = new BufferedReader(new InputStreamReader(fstream));
                while (in.ready()) {
                    old.add(in.readLine());
                }
            }
        } catch (IOException e) {
            System.err.println("IO BADNESS: " + e.getMessage());
        }
        File file = new File("peripherals.txt");
        try {
            FileInputStream fstream = new FileInputStream(file);
            BufferedReader in = new BufferedReader(new InputStreamReader(fstream));
            while (in.ready()) {
                String line = in.readLine();
                tid = line;
            }
        } catch (IOException e) {
            System.err.println("File input error\n" + e.getMessage());
        }
        String cid = tid;
        file = new File("towrite.txt");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException ex) {
                System.err.println("Unable to create file");
            }
        }
        try {
            FileWriter writer = new FileWriter(file);
            BufferedWriter bwriter = new BufferedWriter(writer);
            for (int i = 0; i < old.size(); i++) {
                bwriter.write(old.get(i));
                bwriter.newLine();
            }
            bwriter.write("insert into CUSTOMER values('" + cid + "', 'Guest', '" + ccn + "')");
            bwriter.newLine();
            bwriter.write("insert into CONDUCTS values('" + cid + "', '" + tid + "')");
            bwriter.newLine();
            ONode temp = l.head;
            while (temp != null) {
                int num = 0;
                if (temp.rental) {
                    num = 1;
                    retid = tid;
                    retid = retid.concat("010");
                    bwriter.write("insert into RETTRANSACTION values ('" + retid + "', SYSDATE+30)");
                    bwriter.newLine();
                }
                bwriter.write("insert into CONTAINS values('" + temp.current.getID() + "', '" + tid + "', '" + temp.quant + "', '" + num + "')");
                bwriter.newLine();
                temp = temp.next;
            }
            bwriter.write("insert into TRANSACTION values('" + tid + "', SYSDATE, " + getSubtotal() + ", " + getTax() + ", " + getTotal() + ")");
            bwriter.newLine();
            bwriter.close();
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
        file = new File("peripherals.txt");
        file.delete();
        file = new File("peripherals.txt");
        try {
            file.createNewFile();
            FileWriter writer = new FileWriter(file);
            BufferedWriter bwriter = new BufferedWriter(writer);
            tid = Integer.toString(Integer.parseInt(tid) - 1);
            bwriter.write(tid);
            bwriter.newLine();
            bwriter.close();
        } catch (IOException ex) {
            Logger.getLogger(OSale.class.getName()).log(Level.SEVERE, null, ex);
        }

        if (retid != null) {
            Calendar c = new GregorianCalendar();
            c.add(Calendar.DATE, 30);
            java.util.Date d = c.getTime();
            System.out.println("Your RETURN ID: " + retid + ".\nItems are due back on: " + d.getDate() + "-" + (d.getMonth() + 1) + "-15");
        }
    }

    public boolean checknumber(String cnum) {
        if (cnum.equals("12345")) {
            return true;
        }
        int chars = cnum.length() - 10;
        if (chars < 0) {
            throw new IllegalArgumentException("Credit card number mustbe at least 10 digits");
        }
        cnum = cnum.substring(chars, 10 + chars);
        int sum = 0;
        for (int i = 0; i < cnum.length(); i++) {
            char tmp = cnum.charAt(i);
            int num = tmp - '0';
            int product;
            if (i % 2 != 0) {
                product = num;
            } else {
                product = num * 2;
            }
            if (product > 9) {
                product -= 9;
            }
            sum += product;
        }
        return (sum % 10 == 0);
    }

    public Money getTotal() {
        return total;

    }

    public Money getSubtotal() {
        return subtotal;
    }

    public Money getTax() {
        return tax;
    }

    @Override
    public String toString() {
        String s = "";
        ONode n = l.head;
        while (n != null) {
            if (n.isRental()) {
                s = s + n.toString() + "*\n";
            } else {
                s = s + n.toString() + "\n";
            }
            n = n.next;
        }
        return s;
    }

    void printRecipt() {
        DateFormat df = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        Calendar cal = Calendar.getInstance();
        System.out.println("             What is the Point of Sales\n                " + df.format(cal.getTime()) + "\n                       Thank you\n____________________________________");
        System.out.print(this);
        TaxCalculator tcalc = TaxCalculator.getInstance();
        tax.setValue(tcalc.calculateTax(subtotal));
        total.add(subtotal.getValue(), tax.getValue());
        System.out.println("____________________________________");
        System.out.printf("Subtotal:%45s\n", getSubtotal().toString());
        System.out.printf("Tax:        %45s\n", getTax().toString());
        System.out.printf("Total:     %45s\n", getTotal().toString());

    }

    class OLinkedListOfItems {

        ONode head;
        ONode tail;

        private OLinkedListOfItems() {
            head = null;
            tail = null;
        }

        private boolean findAndUpdate(OItem i, int q, boolean rental) {
            boolean found = false;
            ONode n = head;
            while (n != null) {
                if (n.getItem().isEqual(i) && (rental == n.rental)) {
                    n.setQuant(q);
                    found = true;
                    break;
                }
                n = n.next;
            }
            return found;
        }

        private boolean find(OItem i, boolean rental) {
            boolean found = false;
            ONode n = head;
            while (n != null) {
                if (n.getItem().isEqual(i) && (rental == n.rental)) {
                    found = true;
                    break;
                }
                n = n.next;
            }
            return found;
        }

        private int remove(OItem i, boolean rental) {
            ONode current = head;
            ONode prev = head;
            while (current != null) {
                if (current.getItem().isEqual(i) && rental == current.rental) {
                    if (current == head) {
                        head = head.next;
                    } else {
                        prev.next = current.next;
                    }
                    return current.quant;
                }
                if (current != head) {
                    prev = prev.next;
                }
                current = current.next;
            }
            return 0;
        }

        private void add(OItem i, int q, boolean rental) {
            ONode n = new ONode(i, q, null, rental);
            ONode temp = head;
            if (head == null) {
                head = n;
                tail = n;
            } else if (findAndUpdate(i, q, rental)) {

            } else {
                tail.setNext(n);
                tail = n;
            }
        }
    }

//This is the node class for creating the linked list
    class ONode {

        private ONode next;
        private OItem current;
        private int quant;
        private boolean rental;

        private ONode(OItem current, int quant, ONode next, boolean rental) {
            this.current = current;
            this.quant = quant;
            this.next = next;
            this.rental = rental;
        }

        private void setNext(ONode next) {
            this.next = next;
        }

        private void setQuant(int q) {
            this.quant += q;
        }

        boolean isRental() {
            return rental;
        }

        OItem getItem() {
            return current;
        }

        @Override
        public String toString() {
            return current.getID() + "\t"
                    + quant + "    " + current.getPrice();
        }
    }

    public void fillOItems() {
        File file = new File("items.txt");
        try {
            FileInputStream fstream = new FileInputStream(file);
            BufferedReader in = new BufferedReader(new InputStreamReader(fstream));
            String line = in.readLine();
            while (in.ready()) {
                line = in.readLine();
                String[] a;
                a = line.split("\t");
                items.add(new OItem(Integer.parseInt(a[0]), new Money(Double.parseDouble(a[1]))));
            }
        } catch (IOException e) {
            System.err.println("File input error\n" + e.getMessage());
        }
    }

    public Money checkSKU(int sku) {
        for (int i = 0; i < items.size(); i++) {
            if (sku == items.get(i).getID()) {
                return items.get(i).getPrice();
            }
        }
        return null;
    }
}
