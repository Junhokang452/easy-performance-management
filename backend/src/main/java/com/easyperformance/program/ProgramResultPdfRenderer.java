package com.easyperformance.program;

import com.easyperformance.program.ProgramDtos.GradeCount;
import com.easyperformance.program.ProgramDtos.ParticipantAttributes;
import com.easyperformance.program.ProgramDtos.ResultRow;
import com.easyperformance.program.ProgramResultPdfDtos.*;
import com.easyware.platform.error.ApiException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Component;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@Component
public class ProgramResultPdfRenderer {
    static final int MAX_BYTES=8*1024*1024,MAX_PAGES=40;
    static final Duration MAX_RENDER_TIME=Duration.ofSeconds(5);
    private static final float MARGIN=36,HEADER_HEIGHT=82,FOOTER_HEIGHT=48,ROW_HEIGHT=20;

    public byte[] render(ProgramResultPdfSource source){
        long deadline=System.nanoTime()+MAX_RENDER_TIME.toNanos();
        try(PDDocument document=new PDDocument();InputStream fontStream=fontStream()){
            PDType0Font font=PDType0Font.load(document,fontStream,true);
            RenderState state=new RenderState(document,font,source,deadline);
            try{for(PdfSection section:source.request().sections()){
                if(section==PdfSection.SUMMARY)state.summary();else state.participantTable();
            }}finally{state.close();}
            BoundedByteArrayOutputStream output=new BoundedByteArrayOutputStream(MAX_BYTES,deadline);
            document.save(output);
            return output.toByteArray();
        }catch(OutputLimitException exception){throw invalid("PDF_OUTPUT_LIMIT");}
        catch(RenderTimeoutException exception){throw invalid("PDF_RENDER_TIMEOUT");}
        catch(ApiException exception){throw exception;}
        catch(IOException exception){throw new IllegalStateException("PDF rendering failed",exception);}
    }

    private static InputStream fontStream(){InputStream stream=ProgramResultPdfRenderer.class.getResourceAsStream("/fonts/NanumGothic-Regular.ttf");if(stream==null)throw new IllegalStateException("Bundled PDF font is missing");return stream;}
    private static ApiException invalid(String reason){return new ApiException(ProgramErrorCode.PROGRAM_INVALID,Map.of("reason",reason));}

    private static final class RenderState {
        private final PDDocument document;private final PDType0Font font;private final ProgramResultPdfSource source;private final long deadline;
        private PDPage page;private PDPageContentStream content;private float y;private int pageNumber;
        private RenderState(PDDocument document,PDType0Font font,ProgramResultPdfSource source,long deadline){this.document=document;this.font=font;this.source=source;this.deadline=deadline;}

        private void summary() throws IOException {
            ensureSpace(116,false);text(label("Summary","요약"),MARGIN,y,14);y-=26;
            text(label("Published results: ","공개 결과 수: ")+source.summary().finalizedCount(),MARGIN,y,10);y-=18;
            text(label("Grade distribution:","등급 분포:"),MARGIN,y,10);y-=16;
            if(source.summary().grades().isEmpty()){text("-",MARGIN+10,y,9);y-=14;}
            else for(GradeCount row:source.summary().grades()){ensureSpace(16,false);text(fit(grade(row),usableWidth()-10,9),MARGIN+10,y,9);y-=14;}
            ensureSpace(58,false);
            text(label("Generated (UTC): ","생성 시각(UTC): ")+source.generatedAt(),MARGIN,y,9);y-=16;
            text(label("Source: ","원본 해시: ")+source.sourceHash(),MARGIN,y,7);y-=24;
        }

        private void participantTable() throws IOException {
            ensureSpace(80,true);text(label("Participant results","대상자 결과"),MARGIN,y,14);y-=24;tableHeader();
            for(ResultRow row:source.summary().rows()){
                checkDeadline();List<PdfColumn> columns=source.request().participantColumns();float width=usableWidth()/columns.size();
                List<List<String>> cells=new ArrayList<>();for(PdfColumn column:columns)cells.add(wrap(value(row,column),width-4,8.5f,2));
                int lines=cells.stream().mapToInt(List::size).max().orElse(1);float rowHeight=rowHeight(lines);
                if(y<MARGIN+FOOTER_HEIGHT+rowHeight){newPage();tableHeader();}
                float x=MARGIN;for(List<String> cell:cells){for(int line=0;line<cell.size();line++)text(cell.get(line),x+2,y-line*10,8.5f);x+=width;}
                float separatorY=separatorY(y,rowHeight);line(MARGIN,separatorY,MARGIN+usableWidth(),separatorY);y-=rowHeight;
            }
            y-=10;
        }

        private void tableHeader() throws IOException {
            List<PdfColumn> columns=source.request().participantColumns();float width=usableWidth()/columns.size();float x=MARGIN;
            for(PdfColumn column:columns){cell(columnLabel(column),x,y,width,8);x+=width;}
            line(MARGIN,y-4,MARGIN+usableWidth(),y-4);y-=ROW_HEIGHT;
        }

        private void ensureSpace(float height,boolean table) throws IOException {if(page==null||y-height<MARGIN+FOOTER_HEIGHT)newPage();}
        private void newPage() throws IOException {
            checkDeadline();closeContent();if(pageNumber>=MAX_PAGES)throw invalid("PDF_PAGE_LIMIT");
            PDRectangle rectangle=source.request().orientation()==PdfOrientation.LANDSCAPE
                ?new PDRectangle(PDRectangle.A4.getHeight(),PDRectangle.A4.getWidth()):PDRectangle.A4;
            page=new PDPage(rectangle);document.addPage(page);pageNumber++;content=new PDPageContentStream(document,page);
            float top=rectangle.getHeight()-MARGIN;
            List<String> titleLines=wrap(documentTitle(),usableWidth(),16,2);for(int i=0;i<titleLines.size();i++)text(titleLines.get(i),MARGIN,top-i*19,16);
            text(fit(safe(source.programName()),usableWidth(),10),MARGIN,top-42,10);
            String note=label("Confidential | Long cell content is omitted with ... | Page ","기밀 | 긴 셀은 ...로 생략됩니다 | 페이지 ")+pageNumber;
            text(fit(note,usableWidth(),7),MARGIN,MARGIN-2,7);
            String meta=String.valueOf(source.evaluationYear())+" | "+source.kind()+" | definition r"+source.definitionRevision()+" | UTC "+source.generatedAt();
            text(fit(meta,usableWidth(),6),MARGIN,MARGIN-13,6);text(fit(ProgramResultPdfDtos.POLICY_VERSION+" | Source: "+source.sourceHash(),usableWidth(),6),MARGIN,MARGIN-24,6);
            y=top-HEADER_HEIGHT;
        }

        private String documentTitle(){String title=source.request().title();return title==null?label("Evaluation results","평가 결과"):title;}
        private String grade(GradeCount row){return safe(row.grade())+" "+row.count();}
        private String value(ResultRow row,PdfColumn column){ParticipantAttributes e=row.employee();return switch(column){
            case EMPLOYEE_NO->safe(e.employeeNo());case EMPLOYEE_NAME->safe(e.name());case DEPARTMENT->safe(e.orgUnitName());
            case POSITION->safe(e.positionCode());case JOB->safe(e.jobCode());case SCORE->decimal(row.score());
            case GRADE->safe(row.grade());case FEEDBACK_STATUS->safe(row.feedbackStatus());};}
        private String columnLabel(PdfColumn column){return switch(column){
            case EMPLOYEE_NO->label("Employee no.","사번");case EMPLOYEE_NAME->label("Name","이름");
            case DEPARTMENT->label("Department","부서");case POSITION->label("Position","직책");
            case JOB->label("Job","직무");case SCORE->label("Score","점수");case GRADE->label("Grade","등급");
            case FEEDBACK_STATUS->label("Feedback","피드백");};}
        private String label(String en,String ko){return source.request().locale()==PdfLocale.ko?ko:en;}
        private float usableWidth(){return page.getMediaBox().getWidth()-MARGIN*2;}

        private void cell(String value,float x,float baseline,float width,float size) throws IOException {text(fit(value,width-4,size),x+2,baseline,size);}
        private void text(String value,float x,float baseline,float size) throws IOException {
            checkDeadline();String normalized=safe(value);ensureGlyphs(normalized);content.beginText();content.setFont(font,size);content.newLineAtOffset(x,baseline);content.showText(normalized);content.endText();
        }
        private String fit(String value,float width,float size) throws IOException {
            String normalized=safe(value);if(textWidth(normalized,size)<=width)return normalized;String suffix="...";int[] points=normalized.codePoints().toArray();int low=0,high=points.length;
            while(low<high){int mid=(low+high+1)/2;String candidate=new String(points,0,mid)+suffix;if(textWidth(candidate,size)<=width)low=mid;else high=mid-1;}
            return new String(points,0,low)+suffix;
        }
        private List<String> wrap(String value,float width,float size,int maximumLines) throws IOException {
            String normalized=safe(value);List<String> lines=new ArrayList<>();int[] points=normalized.codePoints().toArray();int offset=0;
            while(offset<points.length&&lines.size()<maximumLines){int low=1,high=points.length-offset,best=0;while(low<=high){int mid=(low+high)/2;String candidate=new String(points,offset,mid);if(textWidth(candidate,size)<=width){best=mid;low=mid+1;}else high=mid-1;}if(best==0)throw invalid("PDF_UNSUPPORTED_GLYPH");boolean last=lines.size()==maximumLines-1&&offset+best<points.length;String line=new String(points,offset,best);if(last)line=fit(line+"...",width,size);lines.add(line);offset+=best;}
            return lines.isEmpty()?List.of("-"):List.copyOf(lines);
        }
        private float textWidth(String value,float size) throws IOException {ensureGlyphs(value);return font.getStringWidth(value)/1000f*size;}
        private void ensureGlyphs(String value) throws IOException {try{font.getStringWidth(value);}catch(IllegalArgumentException exception){throw invalid("PDF_UNSUPPORTED_GLYPH");}}
        private void line(float x1,float y1,float x2,float y2) throws IOException {content.setLineWidth(.3f);content.moveTo(x1,y1);content.lineTo(x2,y2);content.stroke();}
        private void checkDeadline() throws RenderTimeoutException {if(System.nanoTime()>deadline)throw new RenderTimeoutException();}
        private void closeContent() throws IOException {if(content!=null){content.close();content=null;}}
        private void close() throws IOException {closeContent();}
        private static String safe(Object value){if(value==null||String.valueOf(value).isBlank())return "-";StringBuilder out=new StringBuilder();String.valueOf(value).codePoints().forEach(cp->out.appendCodePoint(Character.isISOControl(cp)?' ':cp));return out.toString().trim();}
        private static String decimal(BigDecimal value){return value==null?"-":value.stripTrailingZeros().toPlainString();}
    }

    static float rowHeight(int lines){return lines*10+8;}
    static float separatorY(float rowBaseline,float rowHeight){return rowBaseline-rowHeight+12;}

    private static final class BoundedByteArrayOutputStream extends OutputStream {
        private final ByteArrayOutputStream delegate=new ByteArrayOutputStream();private final int maximum;private final long deadline;
        private BoundedByteArrayOutputStream(int maximum,long deadline){this.maximum=maximum;this.deadline=deadline;}
        @Override public void write(int value) throws IOException {ensure(1);delegate.write(value);}
        @Override public void write(byte[] bytes,int offset,int length) throws IOException {ensure(length);delegate.write(bytes,offset,length);}
        private void ensure(int additional) throws IOException {if(System.nanoTime()>deadline)throw new RenderTimeoutException();if(delegate.size()+additional>maximum)throw new OutputLimitException();}
        private byte[] toByteArray(){return delegate.toByteArray();}
    }
    private static final class OutputLimitException extends IOException {}
    private static final class RenderTimeoutException extends IOException {}
}
