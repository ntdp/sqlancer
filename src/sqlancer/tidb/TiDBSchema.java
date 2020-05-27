package sqlancer.tidb;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import sqlancer.Randomly;
import sqlancer.schema.AbstractSchema;
import sqlancer.schema.AbstractTable;
import sqlancer.schema.AbstractTableColumn;
import sqlancer.schema.AbstractTables;
import sqlancer.schema.TableIndex;
import sqlancer.tidb.TiDBSchema.TiDBTable;

public class TiDBSchema extends AbstractSchema<TiDBTable> {

    public enum TiDBDataType {

        INT, TEXT, BOOL, FLOATING, CHAR, DECIMAL, NUMERIC, BLOB;

        TiDBDataType() {
            isPrimitive = true;
        }

        TiDBDataType(boolean isPrimitive) {
            this.isPrimitive = isPrimitive;
        }

        private final boolean isPrimitive;

        public static TiDBDataType getRandom() {
            return Randomly.fromOptions(values());
        }

        public boolean isPrimitive() {
            return isPrimitive;
        }

        public boolean isNumeric() {
            switch (this) {
            case INT:
            case DECIMAL:
            case FLOATING:
            case BOOL:
            case NUMERIC:
                return true;
            case CHAR:
            case TEXT:
            case BLOB:
                return false;
            default:
                throw new AssertionError(this);
            }
        }
    }

    public static class TiDBCompositeDataType {

        private final TiDBDataType dataType;

        private final int size;

        public TiDBCompositeDataType(TiDBDataType dataType) {
            this.dataType = dataType;
            this.size = -1;
        }

        public TiDBCompositeDataType(TiDBDataType dataType, int size) {
            this.dataType = dataType;
            this.size = size;
        }

        public TiDBDataType getPrimitiveDataType() {
            return dataType;
        }

        public int getSize() {
            if (size == -1) {
                throw new AssertionError(this);
            }
            return size;
        }

        public static TiDBCompositeDataType getInt(int size) {
            return new TiDBCompositeDataType(TiDBDataType.INT, size);
        }

        public static TiDBCompositeDataType getRandom() {
            TiDBDataType primitiveType = TiDBDataType.getRandom();
            int size = -1;
            switch (primitiveType) {
            case INT:
                size = Randomly.fromOptions(1, 2, 4, 8);
                break;
            case FLOATING:
                size = Randomly.fromOptions(4, 8);
                break;
            default:
                break;
            }
            return new TiDBCompositeDataType(primitiveType, size);
        }

        @Override
        public String toString() {
            switch (getPrimitiveDataType()) {
            case INT:
                switch (size) {
                case 1:
                    return "TINYINT";
                case 2:
                    return "SMALLINT";
                case 3:
                    return "MEDIUMINT";
                case 4:
                    return "INTEGER";
                case 8:
                    return "BIGINT";
                default:
                    throw new AssertionError(size);
                }
            case FLOATING:
                switch (size) {
                case 4:
                    return "FLOAT";
                case 8:
                    return "DOUBLE";
                default:
                    throw new AssertionError(size);
                }
            default:
                return getPrimitiveDataType().toString();
            }
        }

    }

    public static class TiDBColumn extends AbstractTableColumn<TiDBTable, TiDBCompositeDataType> {

        private final boolean isPrimaryKey;
        private boolean isNullable;

        public TiDBColumn(String name, TiDBCompositeDataType columnType, boolean isPrimaryKey, boolean isNullable) {
            super(name, null, columnType);
            this.isPrimaryKey = isPrimaryKey;
            this.isNullable = isNullable;
        }

        public boolean isPrimaryKey() {
            return isPrimaryKey;
        }

        public boolean isNullable() {
            return isNullable;
        }

    }

    public static class TiDBTables extends AbstractTables<TiDBTable, TiDBColumn> {

        public TiDBTables(List<TiDBTable> tables) {
            super(tables);
        }

    }

    public TiDBSchema(List<TiDBTable> databaseTables) {
        super(databaseTables);
    }

    public TiDBTables getRandomTableNonEmptyTables() {
        return new TiDBTables(Randomly.nonEmptySubset(getDatabaseTables()));
    }

    private static TiDBCompositeDataType getColumnType(String typeString) {
        typeString = typeString.replace(" zerofill", "").replace(" unsigned", "");
        if (typeString.contains("decimal")) {
            return new TiDBCompositeDataType(TiDBDataType.DECIMAL);
        }
        if (typeString.startsWith("var_string") || typeString.contains("binary")) {
            return new TiDBCompositeDataType(TiDBDataType.TEXT);
        }
        if (typeString.startsWith("char")) {
            return new TiDBCompositeDataType(TiDBDataType.CHAR);
        }
        TiDBDataType primitiveType;
        int size = -1;
        if (typeString.startsWith("bigint")) {
            primitiveType = TiDBDataType.INT;
            size = 8;
        } else {
            switch (typeString) {
            case "text":
            case "longtext":
                primitiveType = TiDBDataType.TEXT;
                break;
            case "float":
            case "double":
                primitiveType = TiDBDataType.FLOATING;
                break;
            case "tinyint(1)":
                primitiveType = TiDBDataType.BOOL;
                break;
            case "null":
                primitiveType = TiDBDataType.INT;
                break;
            case "tinyint(4)":
                primitiveType = TiDBDataType.INT;
                size = 1;
                break;
            case "smallint(6)":
                primitiveType = TiDBDataType.INT;
                size = 2;
                break;
            case "int(11)":
                primitiveType = TiDBDataType.INT;
                size = 4;
                break;
            case "blob":
            case "longblob":
                primitiveType = TiDBDataType.BLOB;
                break;
            default:
                throw new AssertionError(typeString);
            }
        }
        return new TiDBCompositeDataType(primitiveType, size);
    }

    public static class TiDBTable extends AbstractTable<TiDBColumn, TableIndex> {

        public TiDBTable(String tableName, List<TiDBColumn> columns, List<TableIndex> indexes, boolean isView) {
            super(tableName, columns, indexes, isView);
        }

        public boolean hasPrimaryKey() {
            return getColumns().stream().anyMatch(c -> c.isPrimaryKey());
        }

    }

    public static TiDBSchema fromConnection(Connection con, String databaseName) throws SQLException {
        List<TiDBTable> databaseTables = new ArrayList<>();
        List<String> tableNames = getTableNames(con);
        for (String tableName : tableNames) {
            List<TiDBColumn> databaseColumns = getTableColumns(con, tableName);
            List<TableIndex> indexes = getIndexes(con, tableName, databaseName);
            boolean isView = tableName.startsWith("v");
            TiDBTable t = new TiDBTable(tableName, databaseColumns, indexes, isView);
            for (TiDBColumn c : databaseColumns) {
                c.setTable(t);
            }
            databaseTables.add(t);

        }
        return new TiDBSchema(databaseTables);
    }

    private static List<String> getTableNames(Connection con) throws SQLException {
        List<String> tableNames = new ArrayList<>();
        try (Statement s = con.createStatement()) {
            ResultSet tableRs = s.executeQuery("SHOW TABLES");
            while (tableRs.next()) {
                String tableName = tableRs.getString(1);
                tableNames.add(tableName);
            }
        }
        return tableNames;
    }

    private static List<TableIndex> getIndexes(Connection con, String tableName, String databaseName)
            throws SQLException {
        List<TableIndex> indexes = new ArrayList<>();
        try (Statement s = con.createStatement()) {
            try (ResultSet rs = s.executeQuery(String.format("SHOW INDEX FROM %s", tableName))) {
                while (rs.next()) {
                    String indexName = rs.getString("Key_name");
                    indexes.add(TableIndex.create(indexName));
                }
            }
        }
        return indexes;
    }

    private static List<TiDBColumn> getTableColumns(Connection con, String tableName) throws SQLException {
        List<TiDBColumn> columns = new ArrayList<>();
        try (Statement s = con.createStatement()) {
            try (ResultSet rs = s.executeQuery("SHOW COLUMNS FROM " + tableName)) {
                while (rs.next()) {
                    String columnName = rs.getString("Field");
                    String dataType = rs.getString("Type");
                    boolean isNullable = rs.getString("Null").contentEquals("YES");
                    boolean isPrimaryKey = rs.getString("Key").contains("PRI");
                    TiDBColumn c = new TiDBColumn(columnName, getColumnType(dataType), isPrimaryKey, isNullable);
                    columns.add(c);
                }
            }
        }
        return columns;
    }

}
