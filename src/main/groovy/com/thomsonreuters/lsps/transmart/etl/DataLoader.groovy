package com.thomsonreuters.lsps.transmart.etl

import com.thomsonreuters.lsps.transmart.sql.Database
import com.thomsonreuters.lsps.transmart.sql.DatabaseType
import com.thomsonreuters.lsps.transmart.sql.SqlMethods
import groovy.sql.Sql

/**
 * Created by bondarev on 4/8/14.
 */
class DataLoader {
    Database database
    CharSequence tableName
    Collection<CharSequence> columnNames

    DataLoader(Database database, CharSequence tableName, Collection<CharSequence> columnNames) {
        this.database = database
        this.tableName = tableName
        this.columnNames = columnNames.collect { "\"${it}\"" }
    }

    static def start(Database database, CharSequence tableName, Collection<CharSequence> columnNames, Closure block) {
        new DataLoader(database, tableName, columnNames).withBatch(block)
    }

    def withBatch(Closure block) {
        database.withSql { Sql sql->
            //FIXME: find better solution
            if (database.databaseType == DatabaseType.Postgres) {
                columnNames = columnNames*.toLowerCase()
            }
            SqlMethods.insertRecords(sql, tableName, columnNames, block)
        }
    }
}
