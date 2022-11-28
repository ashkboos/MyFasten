/*
 * This file is generated by jOOQ.
 */
package eu.fasten.core.data.metadatadb.codegen.tables;


import eu.fasten.core.data.metadatadb.codegen.Indexes;
import eu.fasten.core.data.metadatadb.codegen.Keys;
import eu.fasten.core.data.metadatadb.codegen.Public;
import eu.fasten.core.data.metadatadb.codegen.enums.Access;
import eu.fasten.core.data.metadatadb.codegen.enums.CallableType;
import eu.fasten.core.data.metadatadb.codegen.tables.records.CallablesRecord;

import java.util.Arrays;
import java.util.List;

import javax.annotation.processing.Generated;

import org.jooq.Check;
import org.jooq.Field;
import org.jooq.ForeignKey;
import org.jooq.Identity;
import org.jooq.Index;
import org.jooq.JSONB;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.Row10;
import org.jooq.Schema;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.TableOptions;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.Internal;
import org.jooq.impl.SQLDataType;
import org.jooq.impl.TableImpl;


/**
 * This class is generated by jOOQ.
 */
@Generated(
    value = {
        "https://www.jooq.org",
        "jOOQ version:3.16.3"
    },
    comments = "This class is generated by jOOQ"
)
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class Callables extends TableImpl<CallablesRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of <code>public.callables</code>
     */
    public static final Callables CALLABLES = new Callables();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<CallablesRecord> getRecordType() {
        return CallablesRecord.class;
    }

    /**
     * The column <code>public.callables.id</code>.
     */
    public final TableField<CallablesRecord, Long> ID = createField(DSL.name("id"), SQLDataType.BIGINT.nullable(false).identity(true), this, "");

    /**
     * The column <code>public.callables.module_id</code>.
     */
    public final TableField<CallablesRecord, Long> MODULE_ID = createField(DSL.name("module_id"), SQLDataType.BIGINT.nullable(false), this, "");

    /**
     * The column <code>public.callables.fasten_uri</code>.
     */
    public final TableField<CallablesRecord, String> FASTEN_URI = createField(DSL.name("fasten_uri"), SQLDataType.CLOB.nullable(false), this, "");

    /**
     * The column <code>public.callables.is_internal_call</code>.
     */
    public final TableField<CallablesRecord, Boolean> IS_INTERNAL_CALL = createField(DSL.name("is_internal_call"), SQLDataType.BOOLEAN.nullable(false), this, "");

    /**
     * The column <code>public.callables.line_start</code>.
     */
    public final TableField<CallablesRecord, Integer> LINE_START = createField(DSL.name("line_start"), SQLDataType.INTEGER, this, "");

    /**
     * The column <code>public.callables.line_end</code>.
     */
    public final TableField<CallablesRecord, Integer> LINE_END = createField(DSL.name("line_end"), SQLDataType.INTEGER, this, "");

    /**
     * The column <code>public.callables.type</code>.
     */
    public final TableField<CallablesRecord, CallableType> TYPE = createField(DSL.name("type"), SQLDataType.VARCHAR.asEnumDataType(eu.fasten.core.data.metadatadb.codegen.enums.CallableType.class), this, "");

    /**
     * The column <code>public.callables.defined</code>.
     */
    public final TableField<CallablesRecord, Boolean> DEFINED = createField(DSL.name("defined"), SQLDataType.BOOLEAN, this, "");

    /**
     * The column <code>public.callables.access</code>.
     */
    public final TableField<CallablesRecord, Access> ACCESS = createField(DSL.name("access"), SQLDataType.VARCHAR.asEnumDataType(eu.fasten.core.data.metadatadb.codegen.enums.Access.class), this, "");

    /**
     * The column <code>public.callables.metadata</code>.
     */
    public final TableField<CallablesRecord, JSONB> METADATA = createField(DSL.name("metadata"), SQLDataType.JSONB, this, "");

    private Callables(Name alias, Table<CallablesRecord> aliased) {
        this(alias, aliased, null);
    }

    private Callables(Name alias, Table<CallablesRecord> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table());
    }

    /**
     * Create an aliased <code>public.callables</code> table reference
     */
    public Callables(String alias) {
        this(DSL.name(alias), CALLABLES);
    }

    /**
     * Create an aliased <code>public.callables</code> table reference
     */
    public Callables(Name alias) {
        this(alias, CALLABLES);
    }

    /**
     * Create a <code>public.callables</code> table reference
     */
    public Callables() {
        this(DSL.name("callables"), null);
    }

    public <O extends Record> Callables(Table<O> child, ForeignKey<O, CallablesRecord> key) {
        super(child, key, CALLABLES);
    }

    @Override
    public Schema getSchema() {
        return aliased() ? null : Public.PUBLIC;
    }

    @Override
    public List<Index> getIndexes() {
        return Arrays.asList(Indexes.CALLABLES_MODULE_ID);
    }

    @Override
    public Identity<CallablesRecord, Long> getIdentity() {
        return (Identity<CallablesRecord, Long>) super.getIdentity();
    }

    @Override
    public UniqueKey<CallablesRecord> getPrimaryKey() {
        return Keys.CALLABLES_PKEY;
    }

    @Override
    public List<UniqueKey<CallablesRecord>> getUniqueKeys() {
        return Arrays.asList(Keys.UNIQUE_URI_CALL);
    }

    @Override
    public List<ForeignKey<CallablesRecord, ?>> getReferences() {
        return Arrays.asList(Keys.CALLABLES__CALLABLES_MODULE_ID_FKEY);
    }

    private transient Modules _modules;

    /**
     * Get the implicit join path to the <code>public.modules</code> table.
     */
    public Modules modules() {
        if (_modules == null)
            _modules = new Modules(this, Keys.CALLABLES__CALLABLES_MODULE_ID_FKEY);

        return _modules;
    }

    @Override
    public List<Check<CallablesRecord>> getChecks() {
        return Arrays.asList(
            Internal.createCheck(this, DSL.name("check_module_id"), "((((module_id = '-1'::integer) AND (is_internal_call IS FALSE)) OR ((module_id IS NOT NULL) AND (is_internal_call IS TRUE))))", true)
        );
    }

    @Override
    public Callables as(String alias) {
        return new Callables(DSL.name(alias), this);
    }

    @Override
    public Callables as(Name alias) {
        return new Callables(alias, this);
    }

    /**
     * Rename this table
     */
    @Override
    public Callables rename(String name) {
        return new Callables(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public Callables rename(Name name) {
        return new Callables(name, null);
    }

    // -------------------------------------------------------------------------
    // Row10 type methods
    // -------------------------------------------------------------------------

    @Override
    public Row10<Long, Long, String, Boolean, Integer, Integer, CallableType, Boolean, Access, JSONB> fieldsRow() {
        return (Row10) super.fieldsRow();
    }
}
