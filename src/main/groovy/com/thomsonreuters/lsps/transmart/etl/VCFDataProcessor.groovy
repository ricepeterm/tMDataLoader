package com.thomsonreuters.lsps.transmart.etl

import com.thomsonreuters.lsps.transmart.files.CsvLikeFile
import com.thomsonreuters.lsps.transmart.files.MetaInfoHeader
import com.thomsonreuters.lsps.transmart.files.VcfFile
import com.thomsonreuters.lsps.transmart.sql.SqlMethods
import groovy.io.FileType
import groovy.sql.Sql

/**
 * Created by bondarev on 4/3/14.
 */
class VCFDataProcessor extends DataProcessor {
    VCFDataProcessor(Object conf) {
        super(conf)
    }

    private void loadMappingFile(File mappingFile, studyInfo) {
        def csv = new CsvLikeFile(mappingFile, '#')
        if (!studyInfo.id) {
            use(MetaInfoHeader) {
                studyInfo.id = csv.metaInfo.STUDY_ID
            }
        }
        def sampleMapping = [:]
        csv.eachEntry {
            String subjectId = it[0]
            String sampleCd = it[1]
            sampleMapping[sampleCd] = subjectId
        }
        studyInfo.sampleMapping = sampleMapping
    }

    @Override
    boolean processFiles(File dir, Sql sql, studyInfo) {
        File mappingFile = new File(dir, 'Subject_Sample_Mapping_File.txt')
        if (!mappingFile.exists()) {
            logger.log(LogType.ERROR, "Mapping file not found")
            return false
        }
        loadMappingFile(mappingFile, studyInfo)
        loadMetadata(sql, studyInfo)
        def samplesLoader = new SamplesLoader(studyInfo.id)
        dir.eachFileMatch(FileType.FILES, ~/(?i).*\.vcf$/) {
            processFile(it, sql, samplesLoader, studyInfo)
        }
        samplesLoader.loadSamples(sql)
        return true
    }

    def loadMetadata(Sql sql, studyInfo) {
        use(SqlMethods) {
            logger.log(LogType.DEBUG, 'Loading study information into deapp.de_variant_dataset')
            sql.insertRecord('deapp.de_variant_dataset',
                    dataset_id: studyInfo.id, etl_id: 'tMDataLoader', genome: 'hg19',
                    etl_date: Calendar.getInstance())
            sql.commit()
        }
    }

    def processFile(File inputFile, Sql sql, SamplesLoader samplesLoader, studyInfo) {
        def vcfFile = new VcfFile(inputFile)
        def sampleMapping = studyInfo.sampleMapping
        String trialId = studyInfo.id
        logger.log(LogType.MESSAGE, "Processing file ${inputFile.getName()}")
        use(SqlMethods) {
            DataLoader.start(database, 'deapp.de_variant_subject_idx', ['dataset_id', 'subject_id', 'position']) { st ->
                vcfFile.samples.eachWithIndex { sample, idx ->
                    logger.log(LogType.DEBUG, 'Loading samples')
                    st.addBatch([trialId, sample, idx + 1])
                    samplesLoader.addSample("VCF+${inputFile.name.replaceFirst(/\.\w+$/, '')}", sampleMapping[sample] as String, sample, '')
                }
            }

            DataLoader.start(database, 'deapp.de_variant_subject_summary',
                    ['dataset_id', 'subject_id', 'rs_id', 'chr', 'pos',
                     'variant', 'variant_format', 'variant_type',
                     'reference', 'allele1', 'allele2']) { st ->
                vcfFile.eachEntry { VcfFile.Entry entry ->
                    CharSequence variantType = entry.reference.size() == 1 &&
                            entry.alternatives.size() == 1 && entry.alternatives[0].size() == 1 ? 'SNV' : 'DIV'
                    entry.samplesData.entrySet().each { sampleEntry ->
                        VcfFile.SampleData sampleData = sampleEntry.value
                        CharSequence variant = ''
                        CharSequence variantFormat = ''
                        Integer allele1 = sampleData.allele1 != '.' ? sampleData.allele1 as int : null
                        Integer allele2 = sampleData.allele2 != '.' ? sampleData.allele2 as int : null
                        boolean reference = false
                        if (sampleData.allele1 == '0') {
                            variant += entry.reference
                            variantFormat += 'R'
                        } else {
                            if (!allele1.is(null)) {
                                variant += entry.alternatives[allele1 - 1]
                                variantFormat += 'V'
                            }
                        }
                        variant += sampleData.alleleSeparator
                        variantFormat += sampleData.alleleSeparator
                        if (sampleData.allele2 == '0') {
                            variant += entry.reference
                            variantFormat += 'R'
                        } else {
                            if (!allele2.is(null)) {
                                variant += entry.alternatives[allele2 - 1]
                                variantFormat += 'V'
                            }
                        }
                        reference = (allele1.is(null) || allele1 == 0) && (allele2.is(null) || allele2 == 0)
                        st.addBatch([trialId, sampleEntry.key, entry.probesetId, entry.chromosome, entry.chromosomePosition,
                                     variant, variantFormat, variantType,
                                     reference, allele1, allele2
                        ])
                    }
                }
            }
        }
    }

    @Override
    boolean runStoredProcedures(Object jobId, Sql sql, Object studyInfo) {
        def studyId = studyInfo['id']
        def studyNode = studyInfo['node']
        if (studyId && studyNode) {
            use(SqlMethods) {
                sql.callProcedure("${config.controlSchema}.i2b2_process_vcf_data",
                        studyId, studyNode, 'STD', config.securitySymbol, jobId)
            }
            return true
        } else {
            logger.log(LogType.ERROR, 'Study ID or Node not defined')
            return false
        }
    }

    @Override
    String getProcedureName() {
        return "${config.controlSchema}.I2B2_PROCESS_VCF_DATA"
    }
}
