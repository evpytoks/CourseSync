using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace CourseSync.Api.Migrations
{
    public partial class RenameCourseNakopToCumulativeGrade : Migration
    {
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.RenameTable(
                name: "course_nakop_elements",
                newName: "course_cumulative_grade_elements");

            migrationBuilder.RenameTable(
                name: "course_nakop",
                newName: "course_cumulative_grade");

            migrationBuilder.RenameIndex(
                name: "IX_course_nakop_elements_course_id",
                table: "course_cumulative_grade_elements",
                newName: "IX_course_cumulative_grade_elements_course_id");

            migrationBuilder.RenameIndex(
                name: "IX_course_nakop_elements_course_id_position",
                table: "course_cumulative_grade_elements",
                newName: "IX_course_cumulative_grade_elements_course_id_position");
        }

        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.RenameIndex(
                name: "IX_course_cumulative_grade_elements_course_id_position",
                table: "course_cumulative_grade_elements",
                newName: "IX_course_nakop_elements_course_id_position");

            migrationBuilder.RenameIndex(
                name: "IX_course_cumulative_grade_elements_course_id",
                table: "course_cumulative_grade_elements",
                newName: "IX_course_nakop_elements_course_id");

            migrationBuilder.RenameTable(
                name: "course_cumulative_grade",
                newName: "course_nakop");

            migrationBuilder.RenameTable(
                name: "course_cumulative_grade_elements",
                newName: "course_nakop_elements");
        }
    }
}
