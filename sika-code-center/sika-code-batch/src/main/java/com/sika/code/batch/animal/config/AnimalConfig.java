package com.sika.code.batch.animal.config;

import com.sika.code.batch.animal.*;
import com.sika.code.batch.animal.listen.AnimalFailureLoggerListener;
import com.sika.code.batch.animal.listen.AnimalListener;
import com.sika.code.batch.animal.listen.AnimalListener.*;
import com.sika.code.batch.animal.mapper.AnimalMapper;
import com.sika.code.batch.animal.service.AnimalService;
import com.sika.code.batch.config.BatchUtil;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.batch.MyBatisBatchItemWriter;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.step.tasklet.TaskletStep;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;

import javax.naming.ServiceUnavailableException;
import javax.sql.DataSource;

/**
 * @author daiqi
 * @create 2019-09-12 0:12
 */
@Configuration
@EnableBatchProcessing
public class AnimalConfig {
    @Autowired
    private SqlSessionFactory sqlSessionFactory;
    @Autowired
    private AnimalService animalService;

    /**
     * ItemReader定义,用来读取数据
     * 1，使用FlatFileItemReader读取文件
     * 2，使用FlatFileItemReader的setResource方法设置csv文件的路径
     * 3，对此对cvs文件的数据和领域模型类做对应映射
     *
     * @return
     * @throws Exception
     */
    @Bean
    public ItemReader<AnimalDTO> readerAnimal() throws Exception {
        FlatFileItemReader<AnimalDTO> reader = new FlatFileItemReader<>();
        reader.setResource(new FileSystemResource("E:\\Users\\animal.csv"));
        String[] names = {"name", "color"};
        reader.setLineMapper(BatchUtil.lineMapper(AnimalDTO.class, "|", names));
        reader.setLinesToSkip(0);
        return reader;
    }


    /**
     * ItemProcessor定义，用来处理数据
     *
     * @return
     */
    @Bean
    public ItemProcessor<AnimalDTO, AnimalEntity> processorAnimal() {
        //使用我们自定义的ItemProcessor的实现CsvItemProcessor
        AnimalItemProcessor processor = new AnimalItemProcessor();
        //为processor指定校验器为CsvBeanValidator()
        processor.setValidator(animalValidator());
        return processor;
    }

    /**
     * ItemWriter定义，用来输出数据
     * spring能让容器中已有的Bean以参数的形式注入，Spring Boot已经为我们定义了dataSource
     *
     * @param dataSource
     * @return
     */
//    @Bean
//    public ItemWriter<AnimalDTO> writerAnimal(@Qualifier("dataSource") DataSource dataSource) {
//        JdbcBatchItemWriter<AnimalDTO> writer = new JdbcBatchItemWriter<>();
//        //我们使用JDBC批处理的JdbcBatchItemWriter来写数据到数据库
//        writer.setItemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>());
//        String sql = "insert into animal " + " (name,color) "
//                + " values(:name,:color)";
//        //在此设置要执行批处理的SQL语句
//        writer.setSql(sql);
//        writer.setDataSource(dataSource);
//        return writer;
//    }
    @Bean
    public ItemWriter<AnimalEntity> writerAnimal(@Qualifier("dataSource") DataSource dataSource) {
        MyBatisBatchItemWriter<AnimalEntity> writer = new MyBatisBatchItemWriter<>();
//        writer.setBaseService(animalService);
        writer.setSqlSessionFactory(sqlSessionFactory);
        writer.setStatementId(AnimalMapper.class.getName() + ".insert");
        writer.afterPropertiesSet();
        return writer;
    }

    /**
     * Job定义，我们要实际执行的任务，包含一个或多个Step
     *
     * @param jobBuilderFactory
     * @param s1
     * @return
     */
    @Bean
    public Job importJob1(JobBuilderFactory jobBuilderFactory, Step step1) {
        return jobBuilderFactory.get("importJob1")
                .incrementer(new RunIdIncrementer())
                .flow(step1)//为Job指定Step
                .end()
                .listener(csvJobListener1())//绑定监听器csvJobListener
                .build();
    }

    /**
     * step步骤，包含ItemReader，ItemProcessor和ItemWriter
     *
     * @param stepBuilderFactory
     * @param readerAnimal
     * @param writerAnimal
     * @param processorAnimal
     * @return
     */
    @Bean
    public Step step1(StepBuilderFactory stepBuilderFactory, ItemReader<AnimalDTO> readerAnimal, ItemWriter<AnimalEntity> writerAnimal,
                      ItemProcessor<AnimalDTO, AnimalEntity> processorAnimal) throws Exception {
        TaskletStep step = stepBuilderFactory
                .get("step1")
                .<AnimalDTO, AnimalEntity>chunk(1000)//批处理每次提交65000条数据
                .reader(readerAnimal)//给step绑定reader
                .listener(new AnimalItemReadListener())
                .processor(processorAnimal)//给step绑定processor
                .listener(new AnimalItemProcessListener())
                .writer(writerAnimal)//给step绑定writer
                .listener(new AnimalItemWriteListener())
                .faultTolerant()
                .skipLimit(10)
                .skip(Exception.class)
                .listener(new AnimalSkipListener())
                .retryLimit(5)
                .retry(ServiceUnavailableException.class)
                .throttleLimit(10)
                .listener(new AnimalChunkListener())
                .listener(new AnimalStepExecutionListener())
                .build();
        return step;
    }

    @Bean
    public AnimalFailureLoggerListener animalFailureLoggerListener() {
        return new AnimalFailureLoggerListener();
    }
    @Bean
    public CsvJobListener1 csvJobListener1() {
        return new CsvJobListener1();
    }

    @Bean
    public AnimalValidator animalValidator() {
        return new AnimalValidator();
    }

}