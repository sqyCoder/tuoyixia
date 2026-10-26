package com.keepup.course;

import android.content.Context;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;

public final class ChaoxingApi {
    public static final String COURSE_LIST =
            "https://mooc1-1.chaoxing.com/visit/courselistdata?courseType=1&courseFolderId=0&baseEducation=0&superstarClass=0";

    private static final Pattern REMAIN = Pattern.compile(
            "剩余\\s*(?:(\\d+)\\s*天)?\\s*(?:(\\d+)\\s*小时)?\\s*(?:(\\d+)\\s*分钟)?");

    public interface Progress {
        void step(String message);
    }

    public static class Course {
        public String name = "";
        public String courseId = "";
        public String classId = "";
        public String cpi = "";
    }

    public static class Snapshot {
        public final List<Course> courses = new ArrayList<>();
        public final List<Task> tasks = new ArrayList<>();
        public String accountId = "";
    }

    public static List<Course> fetchCourses(Context c) throws IOException {
        Http.Result r = Http.get(c, COURSE_LIST, null);
        return parseCourses(r.body);
    }

    public static Snapshot sync(Context c, Progress p) throws IOException {
        Snapshot s = new Snapshot();
        if (p != null) p.step("正在获取课程列表…");
        Http.Result r = Http.get(c, COURSE_LIST, null);
        s.courses.addAll(parseCourses(r.body));
        if (!s.courses.isEmpty()) s.accountId = s.courses.get(0).cpi;

        int n = s.courses.size();
        int i = 0;
        for (Course course : s.courses) {
            i++;
            if (p != null) p.step("适配课程 " + i + "/" + n + "：" + course.name);
            try {
                s.tasks.addAll(fetchWork(c, course));
            } catch (Http.AuthException e) {
                throw e;
            } catch (Exception ignored) {
            }
            if (n > 1) {
                try {
                    Thread.sleep(250);
                } catch (InterruptedException ignored) {
                }
            }
        }
        return s;
    }

    private static List<Course> parseCourses(String html) {
        List<Course> out = new ArrayList<>();
        Document doc = Jsoup.parse(html);
        Elements lis = doc.select("li.course");
        for (Element li : lis) {
            String cid = li.attr("courseid");
            String clz = li.attr("clazzid");
            String pid = li.attr("personid");
            if (cid.isEmpty() || clz.isEmpty()) continue;
            Course c = new Course();
            c.courseId = cid;
            c.classId = clz;
            c.cpi = pid;
            Element name = li.selectFirst(".course-name");
            if (name != null) {
                c.name = name.text().trim();
                if (c.name.isEmpty()) c.name = name.attr("title").trim();
            }
            if (c.name.isEmpty()) c.name = "课程 " + cid;
            boolean dup = false;
            for (Course x : out) {
                if (x.courseId.equals(c.courseId) && x.classId.equals(c.classId)) {
                    dup = true;
                    break;
                }
            }
            if (!dup) out.add(c);
        }
        return out;
    }

    private static List<Task> fetchWork(Context c, Course course) throws IOException {
        List<Task> out = new ArrayList<>();
        String middle = "https://mooc1-1.chaoxing.com/mooc-ans/visit/stucoursemiddle?courseid=" + course.courseId
                + "&clazzid=" + course.classId + "&vc=1&cpi=" + course.cpi + "&ismooc2=1&v=2";
        Http.Result mid = Http.get(c, middle, COURSE_LIST);
        HttpUrl url = HttpUrl.parse(mid.url);
        if (url == null) return out;
        String enc = url.queryParameter("enc");
        String t = url.queryParameter("t");
        if (enc == null || t == null) return out;

        Document midDoc = Jsoup.parse(mid.body);
        Element we = midDoc.selectFirst("#workEnc");
        if (we == null) return out;
        String workEnc = we.attr("value");
        if (workEnc.isEmpty()) return out;

        String workUrl = "https://mooc1.chaoxing.com/mooc2/work/list?courseId=" + course.courseId
                + "&classId=" + course.classId + "&cpi=" + course.cpi + "&ut=s&t=" + t
                + "&stuenc=" + enc + "&enc=" + workEnc;
        Http.Result wr = Http.get(c, workUrl, mid.url);
        Document doc = Jsoup.parse(wr.body);
        long now = System.currentTimeMillis();
        Elements items = doc.select("li[onclick*=goTask]");
        for (Element li : items) {
            String data = li.attr("data");
            if (data.isEmpty()) continue;
            Matcher wm = Pattern.compile("workId=(\\d+)").matcher(data);
            if (!wm.find()) continue;
            Task task = new Task();
            task.workId = wm.group(1);
            task.courseId = course.courseId;
            task.classId = course.classId;
            task.courseName = course.name;
            task.url = data;
            Element title = li.selectFirst(".right-content p");
            if (title == null) title = li.selectFirst("p");
            task.title = title == null ? ("作业 " + task.workId) : title.text().trim();
            Element st = li.selectFirst(".status");
            task.status = st == null ? "" : st.text().trim();
            Element tm = li.selectFirst(".time");
            String remain = tm == null ? "" : tm.text().trim();
            long minutes = parseRemainMinutes(remain);
            if (minutes > 0) task.endAt = now + minutes * 60000L;
            out.add(task);
        }
        return out;
    }

    private static long parseRemainMinutes(String text) {
        if (text == null || text.isEmpty()) return 0;
        Matcher m = REMAIN.matcher(text);
        if (!m.find()) return 0;
        long d = m.group(1) == null ? 0 : Long.parseLong(m.group(1));
        long h = m.group(2) == null ? 0 : Long.parseLong(m.group(2));
        long min = m.group(3) == null ? 0 : Long.parseLong(m.group(3));
        long total = d * 24 * 60 + h * 60 + min;
        return total > 0 ? total : 0;
    }
}
