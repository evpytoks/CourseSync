using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace CourseSync.Api.Migrations
{
    /// <inheritdoc />
    public partial class AddCourseGrading : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<string>(
                name: "grading_text",
                table: "courses",
                type: "character varying(3000)",
                maxLength: 3000,
                nullable: false,
                defaultValue: "");

            migrationBuilder.CreateTable(
                name: "course_grading_elements",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uuid", nullable: false),
                    course_id = table.Column<Guid>(type: "uuid", nullable: false),
                    name = table.Column<string>(type: "character varying(50)", maxLength: 50, nullable: false),
                    coefficient = table.Column<decimal>(type: "numeric(5,4)", nullable: false),
                    position = table.Column<int>(type: "integer", nullable: false),
                    created_at = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_course_grading_elements", x => x.id);
                    table.ForeignKey(
                        name: "FK_course_grading_elements_courses_course_id",
                        column: x => x.course_id,
                        principalTable: "courses",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateIndex(
                name: "IX_course_personal_materials_author_user_id",
                table: "course_personal_materials",
                column: "author_user_id");

            migrationBuilder.CreateIndex(
                name: "IX_course_general_materials_author_user_id",
                table: "course_general_materials",
                column: "author_user_id");

            migrationBuilder.CreateIndex(
                name: "IX_course_grading_elements_course_id",
                table: "course_grading_elements",
                column: "course_id");

            migrationBuilder.CreateIndex(
                name: "IX_course_grading_elements_course_id_position",
                table: "course_grading_elements",
                columns: new[] { "course_id", "position" });
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "course_grading_elements");

            migrationBuilder.DropIndex(
                name: "IX_course_personal_materials_author_user_id",
                table: "course_personal_materials");

            migrationBuilder.DropIndex(
                name: "IX_course_general_materials_author_user_id",
                table: "course_general_materials");

            migrationBuilder.DropColumn(
                name: "grading_text",
                table: "courses");
        }
    }
}
